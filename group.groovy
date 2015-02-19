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
studentlist.splitEachLine(/\s(?=(http|ftp)\S*(.git))/, {
      if(it.size()==2){
            //removing spaces from names
            //def splits = it[0].split()
            //it[0] = ""
            //splits.each({str -> it[0] += str})
            students << [(it[0].tr(" ", "_")) : (it[1])]
      }
      
})
//#DEBUG#
//println students.toMapString()

def common_job = Hudson.instance.getJob(common_jobname)
def new_build
def futures = [:] //List of students + builds assigned to them
//Schedule student builds
students.each({
      student ->
      def params = [
            new StringParameterValue('GITLINK', student.value),
            new StringParameterValue('SLAVENAME', slavename),
            new StringParameterValue('GITBRANCH', gitbranch),
            new StringParameterValue('COMMONJOB', common_jobname),
            new StringParameterValue('JOB', student.key),
         ]
      
      try {
         //Sharing parameters between main build and common_job build
         //main build parameters have to be updated to collect artifacts correctly
         build.addAction(new ParametersAction(params))
         futures << [(student.key) : (common_job.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params)))]
         println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job.url, common_job.fullDisplayName) + " for " + student.key
      } catch (CancellationException x) {
         throw new AbortException("${common_job.fullDisplayName} aborted.")
      }
      catch (NullPointerException x) {
         println "No such common job as $jobname"
         throw new AbortException("$jobname aborted.")
      }
})

//Get results for those builds
futures.each({
      future -> new_build = future.value.get()
      println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " for " + future.key + " completed. Result was " + new_build.result
})

return build.result
