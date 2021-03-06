package dogs

import scala.annotation.tailrec
import scala.annotation.unchecked.uncheckedVariance
import IList.{empty, single}
import cats._
import cats.data._

/**
 * Safe, invariant alternative to stdlib `List`. Most methods on `List` have a sensible equivalent
 * here, either on the `IList` interface itself or via typeclass instances (which are the same as
 * those defined for stdlib `List`). All methods are total and stack-safe.
 */
sealed abstract class IList[A] extends Product with Serializable {

  // Operations, in alphabetic order
  /* alias for `concat` */
  def ++(as: IList[A]): IList[A] =
    concat(as)

  def ++:(as: IList[A]): IList[A] =
    as.concat(this)

  def +:(a: A): IList[A] =
    ::(a)

  /* alias for `foldLeft` */
  def /:[B](b: B)(f: (B, A) => B): B =
    foldLeft(b)(f)

  def :+(a: A): IList[A] =
    concat(single(a))

  def ::(a: A): IList[A] =
    ICons(a, this)

  def :::(as: IList[A]): IList[A] =
    ++:(as)

  /* alias for `foldRight` */
  def :\[B](b: B)(f: (A, B) => B): B =
    foldRight(b)(f)

  /** Returns `f` applied to contents if non-empty, otherwise the zero of `B`. */
  final def <^>[B](f: OneAnd[A, IList] => B)(implicit B: Monoid[B]): B =
    uncons(B.empty, (h, t) => f(OneAnd(h, t)))

  def collect[B](pf: PartialFunction[A,B]): IList[B] = {
    @tailrec def go(as: IList[A], acc: IList[B]): IList[B] =
      as match {
        case ICons(h, t) =>
          if(pf isDefinedAt h) go(t, ICons(pf(h), acc))
          else go(t, acc)
        case INil() => acc
      }
    go(this, empty).reverse
  }

  def collectFirst[B](pf: PartialFunction[A,B]): Option[B] =
    find(pf.isDefinedAt).map(pf)

  def concat(as: IList[A]): IList[A] =
    foldRight(as)(_ :: _)

  // no contains; use Foldable#element

  def containsSlice(as: IList[A])(implicit ev: Eq[A]): Boolean =
    indexOfSlice(as).isDefined

  def count(f: A => Boolean): Int =
    foldLeft(0)((n, a) => if (f(a)) n + 1 else n)

  def drop(n: Int): IList[A] = {
    @tailrec def drop0(as: IList[A], n: Int): IList[A] =
      if (n < 1) as else as match {
        case INil() => empty
        case ICons(_, t) => drop0(t, n - 1)
      }
    drop0(this, n)
  }

  def dropRight(n: Int): IList[A] =
    reverse.drop(n).reverse

  def dropRightWhile(f: A => Boolean): IList[A] =
    reverse.dropWhile(f).reverse

  def dropWhile(f: A => Boolean): IList[A] = {
    @tailrec def dropWhile0(as: IList[A]): IList[A] =
      as match {
        case ICons(h, t) if (f(h)) => dropWhile0(t)
        case a => a
      }
    dropWhile0(this)
  }

  def endsWith(as: IList[A])(implicit ev: Eq[A]): Boolean =
    reverse.startsWith(as.reverse)

  // no exists; use Foldable#any

  def filter(f: A => Boolean): IList[A] =
    foldRight(IList.empty[A])((a, as) => if (f(a)) a :: as else as)

  def filterNot(f: A => Boolean): IList[A] =
    filter(a => !f(a))

  def find(f: A => Boolean): Option[A] = {
    @tailrec def find0(as: IList[A])(f: A => Boolean): Option[A] =
      as match {
        case INil() => None
        case ICons(a, as) => if (f(a)) Some(a) else find0(as)(f)
      }
    find0(this)(f)
  }

  def flatMap[B](f: A => IList[B]): IList[B] =
    foldRight(IList.empty[B])(f(_) ++ _)

  def foldLeft[B](b: B)(f: (B, A) => B): B = {
    @tailrec def foldLeft0(as: IList[A])(b: B)(f: (B, A) => B): B =
      as match {
        case INil() => b
        case ICons(a, as) => foldLeft0(as)(f(b, a))(f)
      }
    foldLeft0(this)(b)(f)
  }

  def foldRight[B](b: B)(f: (A, B) => B): B =
    reverse.foldLeft(b)((b, a) => f(a, b))

  // no forall; use Foldable#all

  def headOption: Option[A] =
    uncons(None, (h, _) => Some(h))

  def headMaybe: Maybe[A] =
    uncons(Maybe.NotThere(), (h, _) => Maybe.There(h))

  def indexOf(a: A)(implicit ev: Eq[A]): Option[Int] =
    indexWhere(ev.eqv(a, _))

  def indexOfSlice(slice: IList[A])(implicit ev: Eq[A]): Option[Int] = {
    @tailrec def indexOfSlice0(i: Int, as: IList[A]): Option[Int] =
      if (as.startsWith(slice)) Some(i) else as match {
        case INil() => None
        case ICons(_, t) => indexOfSlice0(i + 1, t)
      }
    indexOfSlice0(0, this)
  }

  def indexWhere(f: A => Boolean): Option[Int] = {
    @tailrec def indexWhere0(i: Int, as: IList[A]): Option[Int] =
      as match {
        case INil() => None
        case ICons(h, t) => if (f(h)) Some(i) else indexWhere0(i + 1, t)
      }
    indexWhere0(0, this)
  }

  def initOption: Option[IList[A]] =
    reverse.tailOption.map(_.reverse)

  def inits: IList[IList[A]] =
    reverse.tails.map(_.reverse)

  def intersperse(a: A): IList[A] = {
    @tailrec def intersperse0(accum: IList[A], rest: IList[A]): IList[A] = rest match {
      case INil() => accum
      case ICons(x, INil()) => x :: accum
      case ICons(h, t) => intersperse0(a :: h :: accum, t)
    }
    intersperse0(empty, this).reverse
  }

  def isEmpty: Boolean =
    uncons(true, (_, _) => false)

  def lastIndexOf(a:A)(implicit ev: Eq[A]): Option[Int] =
    reverse.indexOf(a).map((length - 1) - _)

  def lastIndexOfSlice(as: IList[A])(implicit ev: Eq[A]): Option[Int] =
    reverse.indexOfSlice(as.reverse).map(length - _ - as.length)

  def lastIndexWhere(f: A => Boolean): Option[Int] =
    reverse.indexWhere(f).map((length - 1) - _)

  @tailrec
  final def lastOption: Option[A] =
    this match {
      case ICons(a, INil()) => Some(a)
      case ICons(_, tail) => tail.lastOption
      case INil() => None
    }

  def length: Int =
    foldLeft(0)((n, _) => n + 1)

  def map[B](f: A => B): IList[B] =
    foldRight(IList.empty[B])(f(_) :: _)

  // private helper for mapAccumLeft/Right below
  private[this] def mapAccum[B, C](as: IList[A])(c: C, f: (C, A) => (C, B)): (C, IList[B]) =
      as.foldLeft((c, IList.empty[B])) {
        case ((c, bs), a) =>
          val (cc,b) = f(c,a)
          (cc,b :: bs)
      }

  /** All of the `B`s, in order, and the final `C` acquired by a stateful left fold over `as`. */
  def mapAccumLeft[B, C](c: C, f: (C, A) => (C, B)): (C, IList[B]) = {
    val (cc,bs) = mapAccum(this)(c, f)
    (cc, bs.reverse)
  }

  /** All of the `B`s, in order `as`-wise, and the final `C` acquired by a stateful right fold over `as`. */
  final def mapAccumRight[B, C](c: C, f: (C, A) => (C, B)): (C, IList[B]) =
    mapAccum(reverse)(c, f)

  // no min/max; use Foldable#minimum, maximum, etc.

  def nonEmpty: Boolean =
    !isEmpty

  def padTo(n: Int, a: A): IList[A] = {
    @tailrec def padTo0(n: Int, init: IList[A], tail: IList[A]): IList[A] =
      if (n < 1) init reverse_::: tail else tail match {
        case INil() => padTo0(n - 1, a :: init, empty)
        case ICons(h, t) => padTo0(n - 1, h :: init, t)
      }
    padTo0(n, empty, this)
  }

  def partition(f: A => Boolean): (IList[A], IList[A]) = {
    val (l,r) = foldLeft((IList.empty[A], IList.empty[A])) {
      case ((ts, fs), a) => if (f(a)) (a :: ts, fs) else (ts, a :: fs)
    }

    (l.reverse, r.reverse)
  }

  def patch(from: Int, patch: IList[A], replaced: Int): IList[A] = {
    val (init, tail) = splitAt(from)
    init ++ patch ++ (tail drop replaced)
  }

  def prefixLength(f: A => Boolean): Int = {
    @tailrec def prefixLength0(n: Int, as: IList[A]): Int =
      as match {
        case ICons(h, t) if (f(h)) => prefixLength0(n + 1, t)
        case _ => n
      }
    prefixLength0(0, this)
  }

  // no product, use Foldable#fold

  def reduceLeftOption(f: (A, A) => A): Option[A] =
    uncons(None, (h, t) => Some(t.foldLeft(h)(f)))

  def reduceRightOption(f: (A, A) => A): Option[A] =
    reverse.reduceLeftOption((a, b) => f(b, a))

  def reverse: IList[A] =
    foldLeft(IList.empty[A])((as, a) => a :: as)

  def reverseMap[B](f: A => B): IList[B] =
    foldLeft(IList.empty[B])((bs, a) => f(a) :: bs)

  def reverse_:::(as: IList[A]): IList[A] =
    as.foldLeft(this)((as, a) => a :: as)

  private[this] def scan0[B](list: IList[A], z: B)(f: (B, A) => B): IList[B] = {
    @tailrec def go(as: IList[A], acc: IList[B], b: B): IList[B] =
      as match {
        case INil() => acc
        case ICons(h, t) =>
          val b0 = f(b, h)
          go(t, b0 :: acc, b0)
      }
    go(list, single(z), z)
  }

  def scanLeft[B](z: B)(f: (B, A) => B): IList[B] =
    scan0(this, z)(f).reverse

  def scanRight[B](z: B)(f: (A, B) => B): IList[B] =
    scan0(reverse, z)((b, a) => f(a, b))

  def slice(from: Int, until: Int): IList[A] =
    drop(from).take((until max 0)- (from max 0))

  def sortBy[B](f: A => B)(implicit B: Order[B]): IList[A] =
    IList(toList.sortBy(f)(algebra.Order.ordering(B)): _*)

  def sorted(implicit ev: Order[A]): IList[A] =
    sortBy(identity)

  def span(f: A => Boolean): (IList[A], IList[A]) = {
    @tailrec def span0(as: IList[A], accum: IList[A]): (IList[A], IList[A]) =
      as match {
        case INil() => (this, empty)
        case ICons(h, t) => if (f(h)) span0(t, h :: accum) else (accum.reverse, as)
      }
    span0(this, empty)
  }

  def splitAt(n: Int): (IList[A], IList[A]) = {
    @tailrec def splitAt0(n: Int, as: IList[A], accum: IList[A]): (IList[A], IList[A]) =
      if (n < 1) (accum.reverse, as) else as match {
        case INil() => (this, empty)
        case ICons(h, t) => splitAt0(n - 1, t, h :: accum)
      }
    splitAt0(n, this, empty)
  }

  def startsWith(as: IList[A])(implicit ev: Eq[A]): Boolean = {
    @tailrec def startsWith0(a: IList[A], b: IList[A]): Boolean =
      (a, b) match {
        case (_, INil()) => true
        case (ICons(ha, ta), ICons(hb, tb)) if ev.eqv(ha, hb) => startsWith0(ta, tb)
        case _ => false
      }
    startsWith0(this, as)
  }

  // no sum, use Foldable#fold

  def tails: IList[IList[A]] = {
    @tailrec def inits0(as: IList[A], accum: IList[IList[A]]): IList[IList[A]] =
      as match {
        case INil() => (as :: accum).reverse
        case ICons(_, t) => inits0(t, as :: accum)
      }
    inits0(this, empty)
  }

  def tailOption: Option[IList[A]] =
    uncons(None, (_, t) => Some(t))

  def take(n: Int): IList[A] = {
    @tailrec def take0(n: Int, as: IList[A], accum: IList[A]): IList[A] =
      if (n < 1) accum.reverse else as match {
        case ICons(h, t) => take0(n - 1, t, h :: accum)
        case INil() => this
      }
    take0(n, this, empty)
  }

  def takeRight(n: Int): IList[A] =
    drop(length - n)

  def takeRightWhile(f: A => Boolean): IList[A] = {
    @tailrec def go(as: IList[A], accum: IList[A]): IList[A] =
      as match {
        case ICons(h, t) if f(h) => go(t, h :: accum)
        case _ => accum
      }
    go(this.reverse, empty)
  }

  def takeWhile(f: A => Boolean): IList[A] = {
    @tailrec def takeWhile0(as: IList[A], accum: IList[A]): IList[A] =
      as match {
        case ICons(h, t) if f(h) => takeWhile0(t, h :: accum)
        case INil() => this
        case _ => accum.reverse
      }
    takeWhile0(this, empty)
  }

  import cats.data.Streaming

  def toStreaming: Streaming[A] =
    uncons(Streaming.empty, (h, t) => Streaming.cons(h, t.toStreaming))

  def toList: List[A] =
    foldRight(Nil : List[A])(_ :: _)

  def toNel: Option[NonEmptyList[A]] =
    uncons(None, (h, t) => Some(NonEmptyList(h, t.toList)))

  def toMap[K, V](implicit ev: A =:= (K, V), ev1: Order[K]): Map[K, V] =
    foldLeft(Map.empty[K,V])((bs, a) => bs + ev(a))

  def toStream: Stream[A] =
    uncons(Stream.empty, (h, t) => h #:: t.toStream)

  override def toString: String =
    IList.show(Show.fromToString).show(this)

  def toVector: Vector[A] =
    foldRight(Vector[A]())(_ +: _)

  def uncons[B](n: => B, c: (A, IList[A]) => B): B =
    this match {
      case INil() => n
      case ICons(h, t) => c(h, t)
    }

  def unzip[B, C](implicit ev: A =:= (B, C)): (IList[B], IList[C]) = {
    val (a,b) = this.map(ev).foldLeft((IList.empty[B], IList.empty[C])) {
      case ((as, bs), (a, b)) => (a :: as, b :: bs)
    }
    (a.reverse, b.reverse)
  }

  /** Unlike stdlib's version, this is total and simply ignores indices that are out of range */
  def updated(index: Int, a: A): IList[A] = {
    @tailrec def updated0(n: Int, as: IList[A], accum: IList[A]): IList[A] =
      (n, as) match {
        case (0, ICons(h, t)) => accum reverse_::: ICons(a, t)
        case (n, ICons(h, t)) => updated0(n - 1, t, h :: accum)
        case _ => this
      }
    updated0(index, this, empty)
  }

  // many other zip variants; see Traverse#zip*

  def zip[B](b: => IList[B]): IList[(A, B)] = {
    @tailrec def zaccum(a: IList[A], b: IList[B], accum: IList[(A,B)]): IList[(A, B)] =
      (a, b) match {
        case (ICons(a, as), ICons(b, bs)) => zaccum(as, bs, (a, b) :: accum)
        case _ => accum
      }
    if(this.isEmpty) empty
    else zaccum(this, b, empty).reverse
  }

  def zipWithIndex: IList[(A, Int)] =
    zip(IList(0 until length : _*))

}

// In order to get exhaustiveness checking and a sane unapply in both 2.9 and 2.10 it seems
// that we need to use bare case classes. Sorry. Suggestions welcome.
final case class INil[A]() extends IList[A]
final case class ICons[A](head: A, tail: IList[A]) extends IList[A]

object IList extends IListInstances with IListFunctions{
  private[this] val nil: IList[Nothing] = INil()

  def apply[A](as: A*): IList[A] =
    as.foldRight(empty[A])(ICons(_, _))

  def single[A](a: A): IList[A] =
    ICons(a, empty)

  def empty[A]: IList[A] =
    nil.asInstanceOf[IList[A]]

  def fromList[A](as: List[A]): IList[A] =
    as.foldRight(empty[A])(ICons(_, _))

  def fromFoldable[F[_]: Foldable, A](as: F[A]): IList[A] =
    Foldable[F].foldRight[A, IList[A]](as, Eval.now(empty))((a,b) => b.map(ICons(a, _))).value

  def fromOption[A](a: Option[A]): IList[A] =
    a.fold(empty[A])(single)

  def fill[A](n: Int)(a: A): IList[A] = {
    @tailrec def go(i: Int, list: IList[A]): IList[A] = {
      if(i > 0) go(i - 1, ICons(a, list))
      else list
    }
    if(n <= 0) empty
    else go(n, empty)
  }

}

sealed trait IListFunctions

sealed abstract class IListInstance0 {

  implicit def equal[A](implicit A0: Eq[A]): Eq[IList[A]] =
    new IListEq[A] {
      val A = A0
    }

}

sealed abstract class IListInstances extends IListInstance0 {

  implicit val instances: Traverse[IList] with MonadCombine[IList] with CoflatMap[IList] =

    new Traverse[IList] with MonadCombine[IList] with CoflatMap[IList] {

      override def map[A, B](fa: IList[A])(f: A => B): IList[B] =
        fa map f

      override def pure[A](a: A): IList[A] =
        single(a)

      override def flatMap[A, B](fa: IList[A])(f: A => IList[B]): IList[B] =
        fa flatMap f

      override def combine[A](a: IList[A], b: IList[A]): IList[A] =
        a ++ b

      override def empty[A]: IList[A] =
        IList.empty[A]

      override def coflatMap[A, B](fa: IList[A])(f: IList[A] => B) =
        fa.uncons(empty, (_, t) => f(fa) :: coflatMap(t)(f))

      override def coflatten[A](a: IList[A]) =
        a.uncons(empty, (_, t) => a :: coflatten(t))

      override def traverse[F[_], A, B](fa: IList[A])(f: A => F[B])(implicit F: Applicative[F]): F[IList[B]] =
        fa.foldRight(F.pure(IList.empty[B]))((a, fbs) => F.map2(f(a), fbs)(_ :: _))

      override def foldLeft[A, B](fa: IList[A], z: B)(f: (B, A) => B) =
        fa.foldLeft(z)(f)

      override def foldRight[A, B](fa: IList[A], z: Eval[B])(f: (A, Eval[B]) => Eval[B]) =
        fa.foldRight(z)((a, b) => f(a, b))

      override def foldMap[A, B](fa: IList[A])(f: A => B)(implicit M: Monoid[B]) =
        fa.foldLeft(M.empty)((b, a) => M.combine(b, f(a)))

    }

  implicit def order[A](implicit A0: Order[A]): Order[IList[A]] =
    new IListOrder[A] {
      val A = A0
    }

  implicit def monoid[A]: Monoid[IList[A]] =
    new Monoid[IList[A]] {
      def combine(f1: IList[A], f2: IList[A]) = f1 ++ f2
      def empty: IList[A] = IList.empty
    }

  implicit def show[A](implicit A: Show[A]): Show[IList[A]] =
    new Show[IList[A]] {
      override def show(as: IList[A]) = {
        @tailrec def commaSep(rest: IList[A], acc: String): String =
          rest match {
            case INil() => acc
            case ICons(x, xs) => commaSep(xs, (acc :+ ",") + A.show(x))
          }
        "[" + (as match {
          case INil() => ""
          case ICons(x, xs) => commaSep(xs, A.show(x))
        }) + "]"
      }
    }

}


private trait IListEq[A] extends Eq[IList[A]] {
  implicit def A: Eq[A]

  @tailrec final override def eqv(a: IList[A], b: IList[A]): Boolean =
    (a, b) match {
      case (INil(), INil()) => true
      case (ICons(a, as), ICons(b, bs)) if A.eqv(a, b) => eqv(as, bs)
      case _ => false
    }

}

private trait IListOrder[A] extends Order[IList[A]] with IListEq[A] {
  implicit def A: Order[A]

  import Ordering._

  @tailrec final def compare(a1: IList[A], a2: IList[A]) =
    (a1, a2) match {
      case (INil(), INil()) => 0
      case (INil(), ICons(_, _)) => -1
      case (ICons(_, _), INil()) => 1
      case (ICons(a, as), ICons(b, bs)) =>
        A.compare(a, b) match {
          case 0 => compare(as, bs)
          case x => x
        }
    }

}
