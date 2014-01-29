package com.stripe.ctf.instantcodesearch

import com.twitter.util.{Future, Promise, FuturePool}
import com.twitter.concurrent.Broker
import org.jboss.netty.handler.codec.http.{HttpResponse, HttpResponseStatus}

class SearchServer(port : Int, id : Int) extends AbstractSearchServer(port, id) {
  val IndexPath = "instantcodesearch-" + id + ".index"
  case class Query(q : String, broker : Broker[SearchResult])
  val indexer = new Indexer
  lazy val searcher = new Searcher(indexer)

  override def healthcheck() = {
    Future.value(successResponse())
  }

  override def isIndexed() = {
    if (indexer.isIndexed) {
      Future.value(successResponse())
    }
    else {
      Future.value(errorResponse(HttpResponseStatus.OK, "Not indexed"))
    }
  }

  def index(path: String) = {
    Future.value(errorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Index not supported, use indexFile instead"))
  }

  def indexFile(abspath: String, relpath: String) = {
    FuturePool.unboundedPool {
//      System.err.println("[node #" + id + "] Indexing path: " + abspath)
      indexer.indexFile(abspath, relpath)
    }

    Future.value(successResponse())
  }

  override def query(q: String) = {
//    System.err.println("[node #" + id + "] Searching for: " + q)
    handleSearch(q)
  }

  def handleSearch(q: String) = {
    val searches = new Broker[Query]()
    searches.recv foreach { q =>
      FuturePool.unboundedPool {searcher.search(q.q, q.broker)}
    }

    val matches = new Broker[SearchResult]()
    val err = new Broker[Throwable]
    searches ! new Query(q, matches)

    val promise = Promise[HttpResponse]
    var results = List[Match]()

    matches.recv foreach { m =>
      m match {
        case m : Match => results = m :: results
        case Done() => promise.setValue(querySuccessResponse(results))
      }
    }

    promise
  }
}
