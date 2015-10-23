import static com.fizzed.blaze.Shells.*
import static com.fizzed.blaze.Contexts.*
import org.unix4j.Unix4j
import org.unix4j.unix.Tail
import java.io.File
import java.time.LocalDate

// required executables
requireExec("fontcustom", "Visit https://github.com/fizzed/font-mfizz/blob/master/DEVELOPMENT.md").run()

// configuration
name = config.getString("font.name")
version = config.getString("font.version")
buildDir = withBaseDir(config.getString("font.build.dir"))
distDir = withBaseDir(config.getString("font.dist.dir"))
srcDir = withBaseDir("src")
fontcustomConfigFile = withBaseDir("src/config.yml")
svgDir = withBaseDir("src/svg")
year = LocalDate.now().getYear();

log.info("Building {} version {}", name, version)
log.info("Build to {}", buildDir)
log.info("Dist to {}", distDir)

def clean() {
    log.info("Deleting dir {}", buildDir)
    exec("rm", "-Rf", buildDir).run()
    exec("rm", "-Rf", context.withBaseDir(".fontcustom-manifest.json")).run()
}

def font_compile() {
    clean()

    // verify fontcustom version
    fontcustomVersion = exec("fontcustom", "-v").readOutput().run().output().trim()
    
    if (!fontcustomVersion.contains("1.3.8")) {
        log.warn("Detected {}! This script only confirmed to work with 1.3.8", fontcustomVersion)
    }
    
    log.info("Compiling glyphs...")
    exec("fontcustom", "compile", "--config=" + fontcustomConfigFile, svgDir).run()
    
    // move the .fontcustom-manifest.json to the right spot
    jsonManifestFile = context.withBaseDir('.fontcustom-manifest.json')
    newJsonManifestFile = new File(buildDir, jsonManifestFile.getName())
    jsonManifestFile.renameTo(newJsonManifestFile)
}

def compile() {
    font_compile()
    
    log.info("Creating improved stylesheet...")
    
    headerFile = new File(srcDir, "header.txt")
    cssFile = new File(buildDir, "font-mfizz.css")
    newCssFile = new File(buildDir, "font-mfizz.new.css")
    
    // stip first 4 lines of css to new css
    Unix4j
        .tail(Tail.Options.s, 4, cssFile)
        .toFile(newCssFile)
    
    // cat header and new css to old css
    Unix4j
        .cat(headerFile, newCssFile)
        .sed('s/\\$\\{VERSION\\}/' + version + '/')
        .sed('s/\\$\\{YEAR\\}/' + year + '/')
        .sed('s/"font-mfizz"/"FontMfizz"/')
        .toFile(cssFile)
        
    // delete the temp new file
    newCssFile.delete()
    
    oldPreviewFile = new File(buildDir, "font-mfizz-preview.html")
    newPreviewFile = new File(buildDir, "preview.html")
    
    oldPreviewFile.renameTo(newPreviewFile)
    
    log.info("Visit file://{}", newPreviewFile.getCanonicalPath())
}

def dist() {
    log.warn("DO NOT SUBMIT PULL REQUESTS THAT INCLUDE THE 'dist' DIR!!")
    
    log.info("Copying build {} to dist {}", buildDir, distDir)
    exec("rm", "-Rf", distDir).run()
    exec("cp", "-Rf", buildDir, distDir).run()
}

def release() {
    // confirm we are not a snapshot
    if (version.endsWith("-SNAPSHOT")) {
        fail("Version ${version} is a snapshot (change blaze.conf then re-run)")
    }
    
    // confirm release notes contains version
    foundVersion =
        Unix4j
            .fromFile(withBaseDir("RELEASE-NOTES.md"))
            .grep("^#### " + version + " - \\d{4}-\\d{2}-\\d{2}\$")
            .toStringResult()
            
    if (foundVersion == null || foundVersion.equals("")) {
        fail("Version ${version} not present in RELEASE-NOTES.md")
    }
    
    compile()
    dist()
    
    // git commit & tag
    exec("git", "commit", "-am", "Preparing for release v" + version).run()
    exec("git", "tag", "v" + version).run()
    
    log.info("Tagged with git. Please run 'git push -u origin' now.")
}