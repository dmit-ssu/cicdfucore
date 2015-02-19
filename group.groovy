import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException
import java.util.regex.*

// Retrieve parameters of the current build
def jobname = build.buildVariableResolver.resolve("JOB")
def gitlink = build.buildVariableResolver.resolve("GITLINK")
def slavecustom = build.buildVariableResolver.resolve("SLAVECUSTOMNAME")
def studentlist = build.buildVariableResolver.resolve("STUDENTLIST")
def gitbranch = build.buildVariableResolver.resolve("GITBRANCH")
// Generate parameters for common build as well as common job name
String common_jobname = "common_" + jobname.toLowerCase()
String slavename = slavecustom.toLowerCase() + "_slave"
//Generating student map
def students = [:]
studentlist.splitEachLine(/\s(?=(http|ftp)\S*(.git))/, { if(it.size()==2) students << [(it[0]) : (it[1])] })
println students.toMapString()

def common_job = Hudson.instance.getJob(common_jobname)
def new_build
def futures[]
students.each({
      student ->
      def params = [
            new StringParameterValue('GITLINK', student.value),
            new StringParameterValue('SLAVENAME', slavename),
            new StringParameterValue('GITBRANCH', gitbranch),
            new StringParameterValue('COMMONJOB', common_jobname),
            new StringParameterValue('JOB', jobname), ///student.key
         ]
      
      try {
         //Sharing parameters between main build and common_job build
         build.addAction(new ParametersAction(params))
         futures.add(common_job.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params)))
         println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job.url, common_job.fullDisplayName)
         //new_build = future.get()
      } catch (CancellationException x) {
         throw new AbortException("${common_job.fullDisplayName} aborted.")
      }
      catch (NullPointerException x) {
         println "No such common job as $jobname"
         throw new AbortException("$jobname aborted.")
      }
      
      //println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " completed. Result was " + new_build.result
      
      // Check that it succeeded
      //build.result = new_build.result
      //throw new AbortException("${new_build.fullDisplayName} failed.")
      //if (new_build.result != Result.SUCCESS && new_build.result != Result.UNSTABLE) {
      // We abort this build right here and now.
        // throw new AbortException("${new_build.fullDisplayName} failed.")
      ///}

})

futures.each(
      {
            future -> new_build = future.get() 
            println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " completed. Result was " + new_build.result
      })
