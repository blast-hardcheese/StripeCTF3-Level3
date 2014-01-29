package com.stripe.ctf.instantcodesearch

import java.io._
import java.util.Arrays
import java.nio.file._
import java.nio.charset._
import java.nio.file.attribute.BasicFileAttributes


/*
  Indexer traverses all files on the disk, maintaining a map of file path -> trigram -> Set[line numbers]
*/

class Indexer {
  def isIndexed = true

  def indexFile(abspath: String, relpath: String): Indexer = {
    println("Stubbed")
    return this
  }
}
