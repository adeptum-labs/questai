<p align="center">
	<img src="docs/assets/questai-logo.svg" alt="QuestAI logo">
</p>

# QuestAI

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)

QuestAI is a Paper Minecraft server plugin that turns villagers into generated quest givers. It scans nearby villages,
keeps them populated, names villagers with AI-generated names, lets players accept generated quests through an inventory
dialogue, tracks quest progress, and rewards players with mcMMO XP.

The current implementation is intentionally small: one Bukkit entry point coordinates a few `SubPlugin` modules, while
the quest system is split across `RandomQuestPlugin` (event handlers and AI generation) and the `quest` package
(`QuestManager`, `QuestProgress`, `DestinationMarkerRenderer`).

## Features

- Repopulates villages around online players when there are fewer villagers than detected beds.
- Assigns generated names to villagers and persists the villager UUID-to-name mapping in `config.yml`.
- Generates short quest titles and descriptions through LangChain4j's OpenAI chat model.
- Supports `KILL`, `COLLECT`, `TREASURE`, and `FIND_NPC` quest objectives.
- Offers quests through a single-row inventory UI with accept and reject buttons.
- Tracks active quest progress with boss bars and event handlers.
- Rewards completed quests through the mcMMO `ExperienceAPI`.
- Includes optional experimental flying pig behavior, currently not wired into the root plugin.

## Architecture

```mermaid
flowchart TD
	PluginYml[plugin.yml] --> Root[Plugin]
	Root --> AutoVillager[AutoVillagerPlugin]
	Root --> RandomQuest[RandomQuestPlugin]
	RandomQuest --> QM[quest.QuestManager]
	RandomQuest --> OpenAI[OpenAiChatModel]
	RandomQuest --> BukkitEvents[Bukkit event handlers]
	RandomQuest --> McMMO[mcMMO ExperienceAPI]
	QM --> QP[quest.QuestProgress]
	QM --> Npc[Npc cache]
	QP --> Quest[Quest]
	Quest --> Objective[QuestObjective]
	RandomQuest --> DMR[quest.DestinationMarkerRenderer]
	AutoVillager --> VillageInfo[model.VillageInfo]
	FlyingPig[FlyingPigPlugin] -. not currently registered .-> Root
```

### Runtime Modules

| Area | Main files | Responsibility |
| --- | --- | --- |
| Plugin entry point | `Plugin`, `plugin.yml` | Starts and stops the subplugins. |
| Village maintenance | `AutoVillagerPlugin`, `VillageInfo` | Detects nearby village blocks and spawns villagers up to bed count. |
| Quest system | `RandomQuestPlugin`, `QuestManager`, `QuestProgress`, `Quest`, `QuestObjective`, `Npc` | Generates dialogue and quests, opens the GUI, tracks progress, and grants rewards. |
| Map rendering | `DestinationMarkerRenderer` | Draws destination markers on quest maps for `TREASURE` and `FIND_NPC` quests. |
| Utility | `EnumUtil` | Random enum value selection. |
| Experimental content | `FlyingPigPlugin` | Floating pig behavior for new chunks; present but not enabled from `Plugin`. |

## Quest Flow

```mermaid
sequenceDiagram
	actor Player
	participant Villager
	participant RandomQuestPlugin
	participant OpenAI as OpenAI chat model
	participant QuestManager
	participant Bukkit
	participant McMMO

	Player->>Villager: Right-click
	Villager->>RandomQuestPlugin: PlayerInteractEntityEvent
	RandomQuestPlugin->>QuestManager: Check cached NPC data
	alt cached quest exists
		RandomQuestPlugin->>Player: Open quest dialogue
	else no cached data
		RandomQuestPlugin->>OpenAI: Generate quest or villager line
		OpenAI-->>RandomQuestPlugin: Title and description
		RandomQuestPlugin->>QuestManager: Store NPC quest data
		RandomQuestPlugin->>Player: Open quest dialogue
	end
	Player->>RandomQuestPlugin: Accept quest in inventory UI
	RandomQuestPlugin->>QuestManager: Assign QuestProgress
	QuestManager->>Bukkit: Create boss bars and progress task
	Player->>Bukkit: Kill, collect, or reach destination
	Bukkit->>RandomQuestPlugin: Progress event
	RandomQuestPlugin->>QuestManager: Increment or complete quest
	RandomQuestPlugin->>McMMO: Award raw XP
```

## Village Scan Flow

```mermaid
flowchart LR
	PlayerTick[Scheduled player scan] --> Location[Player location]
	Location --> Blocks[Count beds and workstations]
	Blocks --> EnoughBlocks{Village blocks found?}
	EnoughBlocks -- no --> Stop[Do nothing]
	EnoughBlocks -- yes --> Villagers[Count nearby villagers]
	Villagers --> Village{Enough villagers to count as village?}
	Village -- no --> Stop
	Village -- yes --> Spawn{Villagers below bed count?}
	Spawn -- no --> Stop
	Spawn -- yes --> Create[Spawn persistent villagers near player]
```

## Configuration

The plugin expects an OpenAI API key in the server-side plugin config:

```yaml
openai.api-key: "your-api-key"
```

`src/main/resources/config.yml` is ignored by Git in this repository. Keep real secrets out of commits and deployment
artifacts that should be shared. If a local `config.yml` exists when packaging, Maven can include it in the plugin jar
because the POM lists it as a resource.

## Testing

```bash
mvn test
```

Tests use JUnit 5 with Mockito to mock Bukkit server types. No live Minecraft server is needed.

## Build And Checks

Requirements:

- JDK 21 or newer
- Maven
- Paper API and mcMMO dependencies available through the configured Maven repositories

Useful commands:

```bash
mvn clean compile
mvn test
mvn pmd:check checkstyle:check
mvn package
```

The project uses:

- `pmd.xml` for PMD rules.
- `checkstyle.xml` for Checkstyle rules.
- `checkstyle-suppress.xml` for narrow Checkstyle XPath suppressions.

