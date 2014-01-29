package com.stripe.ctf.instantcodesearch

import java.io._
import java.nio.file._

import com.twitter.concurrent.Broker

abstract class SearchResult
case class Match(path: String, line: Int) extends SearchResult
case class Done() extends SearchResult

class Searcher(indexer: Indexer)  {
  def search(needle : String, b : Broker[SearchResult]) = {
    val needlegrams = needle.sliding(3).toSet

    for {
      (fngrams, relpath) <- indexer.ngrams
      if((needlegrams intersect fngrams) == needlegrams)
      (line, no) <- indexer.files(relpath).zipWithIndex
      if(line contains needle)
    } yield b !! Match(relpath, no+1)

    b !! new Done()
  }

}
