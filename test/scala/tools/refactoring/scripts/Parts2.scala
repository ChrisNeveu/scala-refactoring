package scala.tools.refactoring.scripts

import scala.tools.refactoring.tests.utils._
import scala.tools.refactoring._
import scala.tools.refactoring.regeneration._
import scala.tools.refactoring.transformation._

import scala.tools.nsc.ast._
import scala.tools.nsc.symtab._
import scala.tools.nsc.util.Position
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.ConsoleReporter

object Parts2 extends CompilerProvider {
  
  def main(args : Array[String]) : Unit = {
    
    object checkClassNames extends global.Traverser {
       override def traverse(t: global.Tree): Unit = t match {
         case global.ClassDef(_, name, _, _) => if(name.toString.head.isLower) println(name)
         case _ => super.traverse(t)
       }
    }
    
    val src = """
      class A {
        def get() {
          println("hi there!")
          val a = 1
/*(*/     val x = a + 1    /*)*/
          x
        }
      }
"""
    
    val file = compile(src)
    
    val refactoring = new Refactoring(global)
    
    import refactoring._
    import refactoring.global._
    
    val tree: Tree = file
    
    refactoring indexFile file
    
    val (from, to) = (src.indexOf("/*(*/"), src.indexOf("/*)*/"))
    
    val selection = new Selection(file, from, to)
    
    val trees = selection.trees
        
    val selectedMethod = selection.enclosingDefDef getOrElse(throw new Exception("no enclosing defdef found"))

    val parameters = inboundLocalDependencies(selection, selectedMethod.symbol)
    
    val call = mkCallDefDef(NoMods, "newMethod", parameters :: Nil, outboundLocalDependencies(selection, selectedMethod.symbol))
 
    val returns = mkReturn(outboundLocalDependencies(selection, selectedMethod.symbol))
    
    val newDef  = mkDefDef(NoMods, "newMethod", parameters :: Nil, selection.trees ::: returns :: Nil)
          
    var newTree = transform(file) {
      case tree @ Template(parents, self, body) if body exists (_ == selectedMethod) =>
        new Template(parents, self, newDef :: body).copyAttrs(tree)
    }
    
    newTree = transform(newTree) {
      case defdef: DefDef if defdef == selectedMethod =>
        refactoring.transform(defdef) {
          case block @ Block(stats, expr) if block == defdef.rhs =>
            cleanNoPos {
              Block(replaceTrees(stats, selection.trees, call), expr).copyAttrs(block)
            }
        }
    }
    
    val result = refactor(file, newTree)
        
    println(result)
    
    exit(0)
  }
}