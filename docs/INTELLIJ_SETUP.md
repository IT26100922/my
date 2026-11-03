# Opening this project in IntelliJ IDEA

The project builds from the command line with no IDE help (`mvn -q clean package`), so anything that goes
wrong here is an IDE setting, not the code. This page fixes the three messages students usually hit.

| Message | Real cause | Fix (section) |
|---|---|---|
| `JDK isn't specified for module 'movie-ticket-booking'` | The project has no SDK selected | [1](#1-select-a-jdk) |
| `Cannot compile module ... configured for JVM target 5` | Module language level left at 5 | [2](#2-set-the-language-level) |
| `java: invalid source release: 17` / `invalid target release` | Groovy/Java compiler using an old SDK | [1](#1-select-a-jdk) then [2](#2-set-the-language-level) |

## 1. Select a JDK

1. **File → Project Structure** (Ctrl+Alt+Shift+S / Cmd+;) → **Project**.
2. **SDK** dropdown → pick a JDK **17 or newer**.
   Nothing there? Click **SDKs → + (Add SDK) → Download JDK…**, *Version* `21`, *Vendor* anything
   (Temurin / JetBrains Runtime), **Download**. Then select it in the **SDK** dropdown.
3. **Language level** → `17 - Sealed types, always-strict floating-point semantics`.
4. **Apply**.

Then make the module follow the project:

5. **Project Structure → Modules → movie-ticket-booking → Dependencies**
   → **Module SDK** = `Project SDK (…)`. **Apply → OK**.

That single step clears *"JDK isn't specified for module"*.

## 2. Set the language level

The pom declares it (`maven.compiler.source/target/release = 17`), so a Maven reload usually applies it.
To reload: **Maven tool window (right edge) → ↻ Reload All Maven Projects**, or right-click `pom.xml` →
*Maven → Reload project*.

If the module still insists on level 5, set it by hand:

1. **Project Structure → Modules → movie-ticket-booking → Sources** → *Language level* = **17**.
2. **Settings → Build, Execution, Deployment → Compiler → Java Compiler** → *Project bytecode version* =
   **17**. Delete any row in the module table below that says `5` (the `-` above the table).
3. **Build → Rebuild Project**.

## 3. When nothing helps: re-import cleanly

A stale `.idea/` folder from an older import keeps the bad settings, and no amount of reloading changes it.

1. **File → Close Project**.
2. In the file manager, delete the `.idea` folder inside the project folder. (Nothing is lost — it only
   holds IDE settings, not code. Do **not** delete `pom.xml`.)
3. **File → Open** → select the project's **`pom.xml`** → *Open as Project*.
4. Accept when IntelliJ offers to import the Maven project; if it asks for a JDK, choose 17 or 21.

After that the Maven importer writes the correct language level, module SDK and source roots by itself.

## 4. Check it worked

- **File → Project Structure → Project**: SDK shows `17`/`21`, Language level `17`.
- **Project** tool window: `src/main/java` is **blue** (Sources), `src/main/resources` is **yellow-ish**
  (Resources). If they are grey, right-click the folder → *Mark Directory as → Sources/Resources Root*.
- Press the green ▶ on `MovieBookingApplication`. The console ends with
  `Tomcat started on port 8080` and `Started MovieBookingApplication`.

If the console instead shows a database error, that is a MySQL problem, not an IDE one — see the
*troubleshooting* section of the main `README.md`. The default login is `root` / `0000`.

## Note for IntelliJ's terminal / Maven runs

IntelliJ passes no environment variables to Maven unless you add them. The project needs none:
the database login is in `src/main/resources/application.properties` (`root` / `0000`).
Only override `DB_USER` / `DB_PASS` if your MySQL password is different.
