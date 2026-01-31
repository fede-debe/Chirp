import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** top level extension to quickly retrieve the version
 *  catalog reference from our project
 *  I can access libraries extension here from wherever
 *  I can reference Project which is gradle convention
 *  plugins are.
 *  */

val Project.libraries: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")