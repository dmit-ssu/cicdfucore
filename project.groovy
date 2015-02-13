import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException

// Retrieve parameters of the current build
def jobname = build.buildVariableResolver.resolve("JOB")
def gituser = build.buildVariableResolver.resolve("GITUSER")
def gitprj = build.buildVariableResolver.resolve("GITNAME")
def slavecustom = build.buildVariableResolver.resolve("SLAVECUSTOMNAME")

// Generate parameters for common build as well as common job name
String githublink = "git@github.com:" + gituser + '/' + gitprj + '.git'
String repolink = "https://github.com/" + gituser + '/' + gitprj + '.git'
String common_jobname = "common_" + jobname.toLowerCase()
String slavename = jobname.toLowerCase() + "_" + slavecustom.toLowerCase() + "_slave"

def common_job = Hudson.instance.getJob(common_jobname)
def new_build
try {
    def params = [
      new StringParameterValue('JOB', jobname),
      new StringParameterValue('GITHUBLINK', githublink),
      new StringParameterValue('REPOLINK', repolink),
      new StringParameterValue('SLAVENAME', slavename),
    ]
    def future = common_job.scheduleBuild2(0, new Cause.UpstreamCause(build), new ParametersAction(params))
    println "Waiting for the completion of " + HyperlinkNote.encodeTo('/' + common_job.url, common_job.fullDisplayName)
    new_build = future.get()
} catch (CancellationException x) {
    throw new AbortException("${common_job.fullDisplayName} aborted.")
}
catch (NullPointerException x) {
    println "No such common job as $jobname"
    throw new AbortException("$jobname aborted.")
}
println HyperlinkNote.encodeTo('/' + new_build.url, new_build.fullDisplayName) + " completed. Result was " + new_build.result

// Check that it succeeded
build.result = new_build.result
if (new_build.result != Result.SUCCESS && new_build.result != Result.UNSTABLE) {
    // We abort this build right here and now.
    throw new AbortException("${new_build.fullDisplayName} failed.")
}
