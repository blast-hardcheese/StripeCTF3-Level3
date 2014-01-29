package com.stripe.ctf.instantcodesearch

import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.{HttpResponse,HttpHeaders,HttpResponseStatus}
import org.jboss.netty.util.CharsetUtil.UTF_8

import argonaut._
import Argonaut._

case class JsonResponse(success: Boolean, results: Option[List[String]])
object Json {
  implicit def JsonResponseCodecJson =
    casecodec2(JsonResponse.apply, JsonResponse.unapply)("success", "results")
}
import Json.JsonResponseCodecJson

class SearchMasterServer(port: Int, id: Int) extends AbstractSearchServer(port, id) {
  val NumNodes = 3

  def this(port: Int) { this(port, 0) }

  val clients = (1 to NumNodes)
    .map { id => new SearchServerClient(port + id, id)}
    .toArray

  override def isIndexed() = {
    val responsesF = Future.collect(clients.map {client => client.isIndexed()})
    val successF = responsesF.map {responses => responses.forall { response =>

        (response.getStatus() == HttpResponseStatus.OK
          && response.getContent.toString(UTF_8).contains("true"))
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "Nodes are not indexed")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "Nodes are not indexed")
      )
    }
  }

  override def healthcheck() = {
    val responsesF = Future.collect(clients.map {client => client.healthcheck()})
    val successF = responsesF.map {responses => responses.forall { response =>
        response.getStatus() == HttpResponseStatus.OK
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      )
    }
  }

  val walker = new Walker
  @volatile var rri = 0

  def index(path: String) = {
    System.err.println(
      "[master] Requesting " + NumNodes + " nodes to index path: " + path
    )
    val futures = walker.walk(path, { (abspath, relpath) =>
      rri = (rri + 1) % NumNodes
      clients(rri).indexFile(abspath, relpath)
    })

    Future.collect(futures.toList).map { _ => successResponse() }
  }

  def indexFile(abspath: String, relpath: String) = {
    Future.value(errorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Index not supported, use indexFile instead"))
  }

  def jsonResponseToHttpResponse(json: JsonResponse): HttpResponse =
    httpResponse(json.asJson.toString, HttpResponseStatus.OK)

  def query(q: String) = {
    val futureResponses: Future[Seq[HttpResponse]] = Future.collect(clients.map {client => client.query(q)})
    var futureJsonResponses: Future[Seq[JsonResponse]] = futureResponses.map({ responses: Seq[HttpResponse] =>
      responses.map( r => r.getContent.toString(UTF_8).decodeOption[JsonResponse] ).flatten
    })

    futureJsonResponses.map( responses =>
      responses.foldLeft(JsonResponse(false, None))({
        case (JsonResponse(_, None), next) => next
        case (JsonResponse(true, Some(files)), JsonResponse(true, Some(nextFiles))) => JsonResponse(true, Some( (files ::: nextFiles) ))
        case (a, _) => a
      })
    ).map({
      case JsonResponse(true, Some(files)) => JsonResponse(true, Some(files.toSet.toSeq.sorted.toList))
      case r => r
    }).map( jsonResponseToHttpResponse )
  }
}
