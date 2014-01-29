package com.stripe.ctf.instantcodesearch

import java.io._
import java.util.Arrays
import java.nio.file._
import java.nio.charset._
import java.nio.file.attribute.BasicFileAttributes

import com.twitter.concurrent.Broker
import com.twitter.util.{Future, FuturePool}

/*
  Indexer traverses all files on the disk, maintaining a map of file path -> trigram -> Set[line numbers]
*/

class Indexer {
  val indexBroker = new Broker[(String, String)]()

  val currentlyProcessing = collection.mutable.Set.empty[Int]
  def isIndexed = { println(currentlyProcessing); currentlyProcessing.isEmpty }

  val ngrams = collection.mutable.Map.empty[Set[String], String]
  val files = collection.mutable.Map.empty[String, Seq[String]]

  def buildProcessor(id: Int) {
    indexBroker.recv.sync() map { case (abspath: String, relpath: String) =>
      FuturePool.unboundedPool {
        currentlyProcessing += id
        process(abspath, relpath)
        currentlyProcessing -= id
      } map { _ => buildProcessor(id) }
    }
  }

  (1 to 3).map(buildProcessor)

  val decoder = Charset.forName("UTF-8").newDecoder()
  decoder onMalformedInput CodingErrorAction.REPORT
  decoder onUnmappableCharacter CodingErrorAction.REPORT

  def process(abspath: String, relpath: String) {
    val bytes = io.Source.fromFile(new java.io.File(abspath)).mkString.getBytes
    if (Arrays.asList(bytes).indexOf(0) > 0)
      return

    try {
      val r = new InputStreamReader(new ByteArrayInputStream(bytes), decoder)
      val strContents = slurp(r)
      val lines = strContents.split("\n")

      files(relpath) = lines
      ngrams(strContents.sliding(3).toSet) = relpath

      //idx.addFile(relpath, strContents)
    } catch {
      case e: IOException => {
        return
      }
    }
  }

  def indexFile(abspath: String, relpath: String): Indexer = {

    indexBroker !! (abspath, relpath)

    return this
  }
}
