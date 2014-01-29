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
  def isIndexed = true

  def buildProcessor(id: Int) {
    indexBroker.recv.sync() map { case (abspath: String, relpath: String) =>
      FuturePool.unboundedPool {
        println(s"$id: Index $abspath")
      } map { _ => buildProcessor(id) }
    }
  }

  (1 to 3).map(buildProcessor)

  val decoder = Charset.forName("UTF-8").newDecoder()
  decoder onMalformedInput CodingErrorAction.REPORT
  decoder onUnmappableCharacter CodingErrorAction.REPORT

  def indexFile(abspath: String, relpath: String): Indexer = {

    indexBroker !! (abspath, relpath)

    return this
  }
}
