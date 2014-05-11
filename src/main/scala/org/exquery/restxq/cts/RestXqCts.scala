/**
 * Copyright © 2014, Adam Retter / EXQuery
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exquery.restxq.cts

import java.net.{URI, URL}
import java.util.jar.{Attributes, Manifest}
import resource._
import scala.util.{Failure, Success, Try}
import scalax.file.{PathSet, Path}
import dispatch._, Defaults._


/**
 * RESTXQ Compatibility Test Suite
 *
 * Executes a Series of Spec tests
 * against a HTTP End-point
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
object RestXqCts extends App {

  case class Config(restxqCtsUri: Option[URI] = None)

  val parser = new scopt.OptionParser[Config]("cts") {
    head("RESTXQ Compatibility Test Suite", getShortVersion())
    help("help") text("Prints this usage text")
    arg[String]("uri") validate { x => Try(URI.create(x)) match {
      case Success(uri) =>
        success
      case Failure(t) =>
        failure(s"Invalid URI: $x")
    }} action { (x, c) => c.copy(restxqCtsUri = Some(URI.create(x))) } text("The URI of the RESTXQ end-point")
  }

  //parse the command line arguments
  parser.parse(args, new Config()) map {
    config =>
      System.exit(runCts(config))
  } getOrElse {
    //arguments are bad, usage message will have been displayed
    System.exit(ExitCodes.IncorrectArguments)
  }

  /**
   * Run's the CTS
   *
   * @return Exit code
   */
  private def runCts(config: Config) : Int = {
    val queries = getRequiredQueries()
    queries.map(storeQuery(config.restxqCtsUri.get, _))

    -1 //TODO
  }

  private def getRequiredQueries(): PathSet[Path] = {
    val url = getClass().getResource("cts.xquery")
    val maybeParent = Path(url.toURI).flatMap(_.parent)
    maybeParent.map {
      parent =>
        parent ** "*.xquery" filterNot { _.name == "cts.xquery"}
    }.getOrElse(PathSet())
  }

  private def storeQuery(restxqCtsUri: URI, query: Path) = {
    val server = url(restxqCtsUri.toString) / "cts" / "store" / query.name
    val req = server.PUT.setContentType("application/xquery", "UTF-8")
    val putted = req <<< query.fileOption.get
    val result = Http(putted OK as.xml.Elem)
  }

  /**
   * Gets the short version of the application
   *
   * @return The short version of the application
   */
  private def getShortVersion(): String = {
    extractFromManifest {
      attributes =>
        attributes.getValue("Implementation-Version")
    }.getOrElse("UNKNOWN")
  }

  /**
   * Extract attributes from the Manifest
   *
   * @param extractor A function that takes the attributes from the Manifest
   *
   * @return The results of the extractor or None otherwise
   */
  private def extractFromManifest[T](extractor: Attributes => T): Option[T] = {
    val clazz = getClass()
    val className = clazz.getSimpleName + ".class"
    val classPath = clazz.getResource(className).toString()
    if (!classPath.startsWith("jar")) {
      None // Class not from JAR
    } else {
      val manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF"
      managed(new URL(manifestPath).openStream()).map {
        is =>
          val manifest = new Manifest(is)
          extractor(manifest.getMainAttributes)
      }.opt
    }
  }
}

/**
 * Exit codes for the
 * application
 */
object ExitCodes {
  val Passed = 0
  val IncorrectArguments = 1
}
