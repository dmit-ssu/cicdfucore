import hudson.model.*
import hudson.AbortException
import hudson.console.HyperlinkNote
import java.util.concurrent.CancellationException

// Retrieve parameters of the current build
def jobname = build.buildVariableResolver.resolve("JOB")
println "JOB=$jobname"

//hey
println "Heeey";
