import org.vpilo.babymonitor.versioning.GitVersionExtension

// build-logic is an included build, so its classes only reach a build script that applies one of its
// plugins. Applying this one is what makes the git version readable, as `gitVersion.info`.
extensions.create<GitVersionExtension>("gitVersion")
