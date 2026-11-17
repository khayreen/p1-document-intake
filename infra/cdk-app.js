// Runs the Java CDK app with the project's Maven wrapper, on Windows or Linux/macOS.
// Paths are quoted because a Windows user folder can contain a space.
const { spawnSync } = require("child_process");
const path = require("path");

const pom = path.join(__dirname, "pom.xml");
let r;
if (process.platform === "win32") {
  const wrapper = path.join(__dirname, "..", "mvnw.cmd");
  r = spawnSync(`"${wrapper}" -q -f "${pom}" compile exec:java`, { stdio: "inherit", shell: true });
} else {
  r = spawnSync(path.join(__dirname, "..", "mvnw"), ["-q", "-f", pom, "compile", "exec:java"], { stdio: "inherit" });
}
process.exit(r.status === null ? 1 : r.status);
