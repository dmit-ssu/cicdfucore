import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException

// Retrieve parameters of the current build
def cicd_jobparam = build.buildVariableResolver.resolve("CICD_JOB")
def cicd_gitlinkparam = build.buildVariableResolver.resolve("CICD_GITLINK")
def cicd_slavecustomparam = build.buildVariableResolver.resolve("CICD_SLAVECUSTOMNAME")
def cicd_gitbranchparam = build.buildVariableResolver.resolve("CICD_GITBRANCH")
// Generate parameters for common build as well as common job name
String common_jobname = "common_" + cicd_jobparam.toLowerCase()
String slavename = cicd_slavecustomparam.toLowerCase() + "_slave"
println build
def params = [
      new StringParameterValue('GITLINK', cicd_jobparam),
      new StringParameterValue('SLAVENAME', slavename),
      new StringParameterValue('GITBRANCH', cicd_gitbranchparam),
      new StringParameterValue('COMMONJOB', common_jobname),
      new StringParameterValue('JOB', build.ToString().tr(" ", "_")),
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

//Running actual job
def common_job = Hudson.instance.getJob(common_jobname)
def new_build
try {
   //Sharing parameters between main build and common_job build
   build.addAction(new ParametersAction(params))
   def future = common_job.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params))
   println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job.url, common_job.fullDisplayName) + " for " + cicd_jobparam
   new_build = future.get()
} catch (CancellationException x) {
   throw new AbortException("${common_job.fullDisplayName} aborted.")
}
catch (NullPointerException x) {
   println "No such common job as $cicd_jobparam"
   throw new AbortException("$cicd_jobparam aborted.")
}

println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " for " + cicd_jobparam + " completed. Result was " + new_build.result

// Check that it succeeded
build.result = new_build.result
if (new_build.result != Result.SUCCESS && new_build.result != Result.UNSTABLE) {
// We abort this build right here and now.
   throw new AbortException("${new_build.fullDisplayName} failed.")
}

return build.result
