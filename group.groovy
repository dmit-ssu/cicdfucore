import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException
import java.util.regex.*

// Retrieve parameters of the current build
def cicd_jobparam = build.buildVariableResolver.resolve("CICD_JOB")
def cicd_gitlinkparam = build.buildVariableResolver.resolve("CICD_GITLINK")
def cicd_slavecustomparam = build.buildVariableResolver.resolve("CICD_SLAVECUSTOMNAME")
def cicd_studentlistparam = build.buildVariableResolver.resolve("CICD_STUDENTLIST")
def cicd_gitbranchparam = build.buildVariableResolver.resolve("CICD_GITBRANCH")
def cicd_core_repo = build.buildVariableResolver.resolve("CICD_CORE_REPO")

// Generate parameters for common build as well as common job name
String common_jobname = "common_" + cicd_jobparam.toLowerCase()
String slavename = cicd_slavecustomparam.toLowerCase() + "_slave"

//Generating student map
def students = [:]
cicd_studentlistparam.splitEachLine(/\s(?=(http|ftp)\S*(.git))/, {
      if(it.size()==2){
            students << [(it[0]) : (it[1])]
      }
      
})
 def params = [
            new StringParameterValue('CICD_CORE_REPO', cicd_core_repo),
            new StringParameterValue('CICD_SLAVENAME', slavename),
            new StringParameterValue('CICD_JOBNAME', common_jobname),
         ]

//Running a clean job first
def common_job_clean = Hudson.instance.getJob("common_clean")
def clean_build
try {
      //Sharing parameters between main build and common_job_clean build
      def future = common_job_clean.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params))
      println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job_clean.url, common_job_clean.fullDisplayName) + " for " + cicd_jobparam
      clean_build = future.get()
} catch (CancellationException x) {
      throw new AbortException("${common_job.fullDisplayName} aborted.")
}
catch (NullPointerException x) {
      println "common_clean job was not found. Have you copied it?"
      throw new AbortException("Cleaning failed.")
}
build.result = clean_build.result
if (clean_build.result != Result.SUCCESS && clean_build.result != Result.UNSTABLE) {
      // We abort this build right here and now.
      throw new AbortException("${clean_build.fullDisplayName} failed.")
}


def common_job = Hudson.instance.getJob(common_jobname)
def new_build
def futures = [:] //List of students + builds assigned to them
//Schedule student builds
students.each({
      student ->
      params = [
            new StringParameterValue('CICD_CORE_REPO', cicd_core_repo),
            new StringParameterValue('CICD_GITLINK', student.value),
            new StringParameterValue('CICD_SLAVENAME', slavename),
            new StringParameterValue('CICD_GITBRANCH', cicd_gitbranchparam),
            new StringParameterValue('CICD_JOBNAME', common_jobname),
            new StringParameterValue('CICD_BUILD', student.key.tr(" ", "_")),
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
         throw new AbortException("$cicd_jobparam aborted.")
      }
})

//Get results for those builds
futures.each({
      future -> new_build = future.value.get()
      println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " for " + future.key + " completed. Result was " + new_build.result
})

return build.result
