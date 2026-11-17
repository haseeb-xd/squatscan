# SquatScan

SquatScan is an MCP server that checks npm packages for signs of **slopsquatting**
before you install them.

AI coding assistants sometimes suggest npm packages that don't exist. Attackers
register those made-up names and fill them with malware, so the suggested
`npm install` succeeds and installs the attacker's code. SquatScan catches these
packages before they are installed.

## How it works

For each package name, SquatScan looks it up on the npm registry and combines these
signals into a risk score from 0 to 100:

- whether the package exists at all
- how close the name is to a popular package (e.g. `lodahs` vs `lodash`)
- how new the package is
- how many downloads it has
- whether it links to a source repository
- how many versions and maintainers it has

The score maps to a verdict:

| Verdict | Score | What to do |
|---|---|---|
| 🟢 GREEN | under 40 | No strong risk signals. Go ahead. |
| 🟡 YELLOW | 40–69 | Some suspicious signals. Check the name before installing. |
| 🔴 RED | 70 or more | Looks like a squatted package. Don't install it. |

Every result includes a `reasons` list that explains which signals affected the score.

## Install

You need [Docker](https://www.docker.com/) installed and running.

**Claude Code:**

```bash
claude mcp add -s user squatscan -- docker run -i --rm ghcr.io/haseeb-xd/squatscan:0.1.0
```

**Claude Desktop** (or any MCP client): add this to your config file
(Settings → Developer → Edit Config), then fully restart the app:

```json
{
  "mcpServers": {
    "squatscan": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "ghcr.io/haseeb-xd/squatscan:0.1.0"]
    }
  }
}
```

> The `-i` flag is required. Without it, Docker closes stdin and the server exits
> immediately.

SquatScan is also listed in the [MCP Registry](https://registry.modelcontextprotocol.io)
as `io.github.haseeb-xd/squatscan`.

<details>
<summary>Run without Docker (Java 17+)</summary>

```bash
./mvnw clean package -DskipTests
mkdir -p ~/.squatscan && cp target/squatscan-0.1.0.jar ~/.squatscan/squatscan.jar
claude mcp add -s user squatscan -- java -jar "$HOME/.squatscan/squatscan.jar"
```

On Windows, use an absolute path with forward slashes, e.g.
`C:/Users/<you>/.squatscan/squatscan.jar`.
</details>

## Usage

Start a new Claude session, then ask things like:

- `Is lodahs safe to install?`
- `Check react, reqeusts and some-hallucinated-pkg-xyz`
- `I'm about to run npm install express lodahs. Is that OK?`

### Tools

| Tool | What it does |
|---|---|
| `check_package(name, version?)` | Checks one package |
| `check_packages(names[])` | Checks several packages at once |
| `check_install_command(command)` | Reads a command like `npm install a b c` and checks every package in it |

### Make Claude check every time (recommended)

Claude decides for itself when to call an MCP tool, and it often skips SquatScan.
To make it check every time, add this to `~/.claude/CLAUDE.md`:

```markdown
Before recommending or installing ANY npm package, check every package name with the
squatscan MCP tools. RED: don't install it and suggest the intended package.
YELLOW: warn me and ask before continuing. GREEN: go ahead.
```

## Troubleshooting

- **Shows "failed" in `/mcp`:** make sure Docker is running (or that `java` is on your
  PATH if you're not using Docker). Run `claude --debug` to see startup errors.
- **New tools don't appear:** start a new Claude session. Sessions that are already
  running don't pick up newly added servers.

## Scope

npm packages only. Runs entirely on your machine; the only network calls go to the
public npm registry. No accounts or data collection.
