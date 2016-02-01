package edu.colorado.hopper.driver

import java.io.File

import edu.colorado.hopper.client._
import edu.colorado.hopper.client.android._
import edu.colorado.hopper.client.bounds.{ArrayBoundsClient, ArrayBoundsClientTests}
import edu.colorado.walautil.Util
import edu.colorado.thresher.core.Options
import org.json4s._
import org.json4s.native.JsonMethods

object Main {
  
  val REGRESSION = "__regression"
    
  def main(args: Array[String]) : Unit = {     
    val target = Options.parseArgs(args)          
    
    if (target == null) println("No analysis targets given...exiting.")
    else if (target.equals(REGRESSION)) {
      val prevDebug = Options.DEBUG
      val clientTests = 
        if (Options.CHECK_ANDROID_LEAKS) List(AndroidLeakClientTests)
        else if (Options.CHECK_CASTS) List(DowncastCheckingClientTests)
        else if (Options.CHECK_ASSERTS) List(AssertionCheckingClientTests)
        else if (Options.CHECK_ARRAY_BOUNDS) List(ArrayBoundsClientTests)
        else if (Options.CHECK_ANDROID_DEREFS) List(AndroidNullDereferenceClientTests)
        else
          List(AndroidLeakClientTests, DowncastCheckingClientTests, ArrayBoundsClientTests,
               AndroidNullDereferenceClientTests)
      
      val singleTest = Options.TEST
      def runTests(runPiecewise : Boolean = false) : Unit = clientTests.foreach(client => {
        Options.JUMPING_EXECUTION = runPiecewise
        Options.BACKTRACK_JUMPING = runPiecewise
        Options.TEST = singleTest
        if (client.isPiecewiseCompatible || !runPiecewise) {
          println(s"Running tests for client ${client.getClass.getName()}")
          client.runRegressionTests
          Options.restoreDefaults() // reset default values for option flags, including JUMPING_EXECUTION
          Options.DEBUG = prevDebug
        }             
      })
      
      val runPiecewise = Options.JUMPING_EXECUTION
      // run tests without piecewise      
      println("Running regular tests")
      runTests()
      if (runPiecewise) { // run tests with piecewise 
        println("Running piecewise tests")
        runTests(runPiecewise = true)
      }
    } else {
      val client : Client[_] =
        if (Options.CHECK_CASTS) {
          Options.PRINT_REFS = false
          Options.EXIT_ON_FAIL = false
          new DowncastCheckingClient(Options.APP, Util.strToOption(Options.LIB), Options.MAIN_CLASS, Options.MAIN_METHOD)
        } else if (Options.CHECK_ANDROID_LEAKS)
          new AndroidLeakClient(Options.APP, new File(Options.ANDROID_JAR), Util.strToOption(Options.LIB),
            "Landroid/app/Activity", Options.MAIN_METHOD)
        else if (Options.CHECK_ARRAY_BOUNDS)
          new ArrayBoundsClient(Options.APP, Util.strToOption(Options.LIB), Options.MAIN_CLASS, Options.MAIN_METHOD)
        else if (Options.CHECK_CONSTANT_FLOW){
          val json_in = JsonMethods.parse(Options.JSON_BUG_SPEC)
          val user   = (json_in \\ "user_name").asInstanceOf[JString].s
          val repo   = (json_in \\ "repo_name").asInstanceOf[JString].s
          val parent = (json_in \\ "parent_hash").asInstanceOf[JString].s
          val child  = (json_in \\ "child_hash").toOption map {_.asInstanceOf[JString].s}
          val alarms = (json_in \\ "alarms").asInstanceOf[JArray].arr
            .map{jval => ((jval \\ "filename").asInstanceOf[JString].s, (jval \\ "location").asInstanceOf[JInt].num.toInt)}
          //TODO(benno) construct app path from json info
          new AndroidConstantFlowClient(Options.APP, new File(Options.ANDROID_JAR), Options.MUSE_BUG_TYPE, alarms)}
        else if (Options.CHECK_ASSERTS)
          new AssertionCheckingClient(Options.APP, Util.strToOption(Options.LIB), Options.MAIN_CLASS, Options.MAIN_METHOD)
        else if (Options.CHECK_NULLS)
          new NullDereferenceClient(Options.APP, Util.strToOption(Options.LIB), Options.MAIN_CLASS, Options.MAIN_METHOD)
        else if (Options.CHECK_ANDROID_DEREFS)
          new AndroidNullDereferenceClient(Options.APP, new File(Options.ANDROID_JAR))
        else if (Options.CHECK_DIV_BY_ZERO)
          new DivideByZeroClient(Options.APP, Util.strToOption(Options.LIB), Options.MAIN_CLASS, Options.MAIN_METHOD)
        else sys.error("No clients given. Exiting.")
      client.check
    }
  }         
}
