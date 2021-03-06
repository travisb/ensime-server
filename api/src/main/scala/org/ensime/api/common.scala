// Copyright: 2010 - 2017 https://github.com/ensime/ensime-server/graphs
// License: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.api

import java.io.File
import java.nio.file.Path

import scala.annotation.StaticAnnotation

/**
 * Indicates that something will be removed.
 *
 * WORKAROUND https://issues.scala-lang.org/browse/SI-7934
 */
class deprecating(detail: String = "") extends StaticAnnotation

sealed abstract class DeclaredAs(val symbol: scala.Symbol)

object DeclaredAs {
  case object Method extends DeclaredAs('method)
  case object Trait extends DeclaredAs('trait)
  case object Interface extends DeclaredAs('interface)
  case object Object extends DeclaredAs('object)
  case object Class extends DeclaredAs('class)
  case object Field extends DeclaredAs('field)
  case object Nil extends DeclaredAs('nil)

  def allDeclarations = Seq(Method, Trait, Interface, Object, Class, Field, Nil)
}

sealed trait FileEdit extends Ordered[FileEdit] {
  def file: File
  def text: String
  def from: Int
  def to: Int

  // Required as of Scala 2.11 for reasons unknown - the companion to Ordered
  // should already be in implicit scope
  import scala.math.Ordered.orderingToOrdered

  def compare(that: FileEdit): Int =
    (this.file, this.from, this.to, this.text).compare((that.file, that.from, that.to, that.text))
}

final case class TextEdit(file: File, from: Int, to: Int, text: String) extends FileEdit

// the next case classes have weird fields because we need the values in the protocol
final case class NewFile(file: File, from: Int, to: Int, text: String) extends FileEdit
object NewFile {
  def apply(file: File, text: String): NewFile = new NewFile(file, 0, text.length - 1, text)
}

final case class DeleteFile(file: File, from: Int, to: Int, text: String) extends FileEdit
object DeleteFile {
  def apply(file: File, text: String): DeleteFile = new DeleteFile(file, 0, text.length - 1, text)
}

sealed trait NoteSeverity
case object NoteError extends NoteSeverity
case object NoteWarn extends NoteSeverity
case object NoteInfo extends NoteSeverity
object NoteSeverity {
  def apply(severity: Int) = severity match {
    case 2 => NoteError
    case 1 => NoteWarn
    case 0 => NoteInfo
  }
}

sealed abstract class RefactorLocation(val symbol: Symbol)

object RefactorLocation {
  case object QualifiedName extends RefactorLocation('qualifiedName)
  case object File extends RefactorLocation('file)
  case object NewName extends RefactorLocation('newName)
  case object Name extends RefactorLocation('name)
  case object Start extends RefactorLocation('start)
  case object End extends RefactorLocation('end)
  case object MethodName extends RefactorLocation('methodName)
}

sealed abstract class RefactorType(val symbol: Symbol)

object RefactorType {
  case object Rename extends RefactorType('rename)
  case object ExtractMethod extends RefactorType('extractMethod)
  case object ExtractLocal extends RefactorType('extractLocal)
  case object InlineLocal extends RefactorType('inlineLocal)
  case object OrganizeImports extends RefactorType('organizeImports)
  case object AddImport extends RefactorType('addImport)

  def allTypes = Seq(Rename, ExtractMethod, ExtractLocal, InlineLocal, OrganizeImports, AddImport)
}

/**
 * Represents a source file that has a physical location (either a
 * file or an archive entry) with (optional) up-to-date information in
 * another file, or as a String.
 *
 * Clients using a wire protocol should prefer `contentsIn` for
 * performance (string escaping), whereas in-process clients should
 * use the `contents` variant.
 *
 * If both contents and contentsIn are provided, contents is
 * preferred.
 */
final case class SourceFileInfo(
    file: EnsimeFile,
    contents: Option[String] = None,
    contentsIn: Option[File] = None
) {
  // keep the log file sane for unsaved files
  override def toString = s"SourceFileInfo($file,${contents.map(_ => "...")},$contentsIn)"
}

final case class OffsetRange(from: Int, to: Int)

@deprecating("move all non-model code out of the api")
object OffsetRange extends ((Int, Int) => OffsetRange) {
  def apply(fromTo: Int): OffsetRange = new OffsetRange(fromTo, fromTo)
}

// it would be good to expand this hierarchy and include information
// such as files/dirs, existance, content hints
// (java/scala/class/resource) in the type, validated at construction
// (and can be revalidated at any time)
sealed trait EnsimeFile
final case class RawFile(file: Path) extends EnsimeFile
/**
 * @param jar the container of entry (in nio terms, the FileSystem)
 * @param entry is relative to the container (this needs to be loaded by a FileSystem to be usable)
 */
final case class ArchiveFile(jar: Path, entry: String) extends EnsimeFile
