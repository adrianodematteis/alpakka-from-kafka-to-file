package com.vodafone.utils

import java.io.File

object Util { //TODO - modificare per SFTP
  def getListOfFiles(dir: File, filePrefixes: List[String], fileExtensions: List[String]): List[File] = {
    dir.listFiles.filter(_.isFile).toList.filter { file =>
      filePrefixes.exists(file.getName.startsWith(_)) && fileExtensions.exists(file.getName.endsWith(_))
    }
  }

}
