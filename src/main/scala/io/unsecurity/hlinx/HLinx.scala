package io.unsecurity.hlinx

object HLinx {
  sealed trait HList
  case class HCons[H, T <: HList](head: H, tail: T) extends HList {
    def :::[V](v: V): HCons[V, H ::: T] = HCons(v, this)
  }
  final class HNil extends HList {
    def :::[T](v: T): HCons[T, HNil] = HCons(v, this)
  }
  val HNil: HNil = new HNil
  type :::[H, T <: HList] = HCons[H, T]
  val ::: = HCons

  def param[A](name: String)(implicit ppc: PathParamConverter[A])   = Param(name, ppc)
  def qParam[A](name: String)(implicit qpc: QueryParamConverter[A]) = QueryParam[A](name, qpc)

  implicit class QpOps[A](qp: QueryParam[A]) {
    def &[B](other: QueryParam[B]): QueryParam[B] ::: QueryParam[A] ::: HNil =
      other ::: qp ::: HNil
  }

  implicit class HListOfQpOps[A <: HList](qp: A) {
    def &[B](other: QueryParam[B]) =
      HCons(other, qp)
  }

  def splitPath(path: String): List[String] = path.split("/").toList.filter(_.nonEmpty)

  sealed trait HLinx[T <: HList] {
    def /(element: String): Static[T] = {
      splitPath(element).tail
        .foldLeft(Static(this, splitPath(element).head)) { (acc, e) =>
          Static(acc, e)
        }
    }
    def /[H](h: Param[H])                             = Variable(this, h.converter, h.name)
    def capture(s: String): Option[Either[String, T]] = extract(splitPath(s).reverse)
    def extract(s: List[String]): Option[Either[String, T]]
    def overlaps[O <: HList](other: HLinx[O]): Boolean
  }

  case object Root extends HLinx[HNil] {
    override def extract(s: List[String]): Option[Either[String, HNil]] =
      if (s.isEmpty) Some(Right(HNil)) else None

    override def overlaps[O <: HList](other: HLinx[O]): Boolean =
      other match {
        case Root => true
        case _    => false
      }
  }

  case class Static[A <: HList](parent: HLinx[A], element: String) extends HLinx[A] {
    override def extract(s: List[String]): Option[Either[String, A]] = s match {
      case `element` :: rest => parent.extract(rest)
      case _                 => None
    }
    override def overlaps[O <: HList](other: HLinx[O]): Boolean =
      other match {
        case Root                              => false
        case Static(otherParent, otherElement) => element == otherElement && parent.overlaps(otherParent)
        case Variable(otherParent, _, _)       => parent.overlaps(otherParent)
      }
  }
  case class Variable[H, T <: HList](parent: HLinx[T], P: PathParamConverter[H], element: String) extends HLinx[H ::: T] {
    override def extract(s: List[String]): Option[Either[String, H ::: T]] = s match {
      case h :: rest =>
        parent
          .extract(rest)
          .map(t =>
            for {
              hlist          <- t
              convertedParam <- P.convert(h)
            } yield {
              HCons(convertedParam, hlist)
          })
      case _ => None
    }

    def overlaps[A <: HList](hlinx: HLinx[A]): Boolean = {
      hlinx match {
        case Root                        => false
        case Static(otherParent, _)      => parent.overlaps(otherParent)
        case Variable(otherParent, _, _) => parent.overlaps(otherParent)
      }
    }
  }

  implicit class S0(private val hlist: HNil) extends AnyVal {
    def tupled(): Unit = ()
  }

  implicit class S1[A](private val hlist: A ::: HNil) extends AnyVal {
    def tupled: A = hlist.head
  }

  implicit class S2[A, B](private val hlist: B ::: A ::: HNil) extends AnyVal {
    def tupled: (A, B) = (hlist.tail.head, hlist.head)
  }

  implicit class S3[A, B, C](private val hlist: C ::: B ::: A ::: HNil) extends AnyVal {
    def tupled: (A, B, C) = (hlist.tail.tail.head, hlist.tail.head, hlist.head)
  }

  implicit class S4[A, B, C, D](private val hlist: D ::: C ::: B ::: A ::: HNil) extends AnyVal {
    def tupled: (A, B, C, D) = (hlist.tail.tail.tail.head, hlist.tail.tail.head, hlist.tail.head, hlist.head)
  }
}
