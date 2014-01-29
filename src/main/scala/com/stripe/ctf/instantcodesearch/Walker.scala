package com.stripe.ctf.instantcodesearch

import java.nio.file.{FileSystems, Files, SimpleFileVisitor, FileVisitResult, Path, LinkOption}
import java.nio.file.attribute.BasicFileAttributes

import com.twitter.util.Future

/*
  Indexer traverses all files on the disk, maintaining a map of file path -> trigram -> Set[line numbers]
*/

class Walker {
  type AbsolutePath = String
  type RelativePath = String

  def walk[A](path: String, callback: Function2[AbsolutePath, RelativePath, Future[A]]): Seq[Future[A]] = {
    val r = scala.collection.mutable.MutableList.empty[Future[A]]

    val root = FileSystems.getDefault().getPath(path)
    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir : Path, attrs : BasicFileAttributes) : FileVisitResult = {
        if (Files.isHidden(dir) && dir.toString != ".")
          return FileVisitResult.SKIP_SUBTREE
        return FileVisitResult.CONTINUE
      }
      override def visitFile(file : Path, attrs : BasicFileAttributes) : FileVisitResult = {
        if (Files.isHidden(file))
          return FileVisitResult.CONTINUE
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
          return FileVisitResult.CONTINUE
        if (Files.size(file) > (1 << 20))
          return FileVisitResult.CONTINUE

        val key = root.relativize(file).toString

        r += callback(file.toString, key)

        return FileVisitResult.CONTINUE
      }
    })

    r
  }
}

