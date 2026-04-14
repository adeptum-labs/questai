<p align="center">
	<img src="docs/assets/questai-logo.svg" alt="QuestAI logo">
</p>

# QuestAI

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL_v3-blue.svg)](https://www.gnu.org/licenses/lgpl-3.0)

QuestAI is a Paper Minecraft server plugin that turns villagers and wandering peasants into AI-driven quest givers.
It scans nearby villages, keeps them populated, names every villager with AI-generated names, lets players discover
quests through natural conversation, tracks multiple quest progress simultaneously, and rewards players with mcMMO XP.

## Features

- Repopulates villages around online players when there are fewer villagers than detected beds.
- Assigns AI-generated names to villagers and persists the UUID-to-name mapping in `config.yml`.
- Conversational dialogue system with AI-driven NPC greetings, casual chat, and quest narratives.
- Quest discovery through organic NPC conversation — NPCs hint they need help rather than showing explicit quest buttons.
- Supports multiple concurrent quests per player with `KILL`, `COLLECT`, `TREASURE`, and `FIND_NPC` objectives.
- Interactive quest log book — right-click to view all active quests, drop to abandon all quests.
- Wandering peasant NPCs that roam the world and offer quests to players they encounter.
- Tracks quest progress with boss bars and event handlers.
- Rewards completed quests through the mcMMO `ExperienceAPI`.

## Architecture

```mermaid
flowchart TD
	PluginYml[plugin.yml] --> Root[Plugin]
	Root --> AutoVillager[AutoVillagerPlugin]
	Root --> RandomQuest[RandomQuestPlugin]
	Root --> WanderingPeasant[WanderingPeasantPlugin]
	Root --> QuestLogListener[QuestLogListener]
	RandomQuest --> QM[quest.QuestManager]
	RandomQuest --> ConvMgr[dialogue.ConversationManager]
	WanderingPeasant --> ConvMgr
	ConvMgr --> DialogueGui[dialogue.DialogueGui]
	ConvMgr --> DialoguePrompts[dialogue.DialoguePrompts]
	ConvMgr --> OpenAI[OpenAiChatModel]
	QM --> QP[quest.QuestProgress]
	QM --> QLB[quest.QuestLogBook]
	QM --> Npc[Npc cache]
	QP --> Quest[Quest]
	Quest --> Objective[QuestObjective]
	QuestLogListener --> QM
	QuestLogListener --> QLG[quest.QuestLogGui]
	RandomQuest --> McMMO[mcMMO ExperienceAPI]
	RandomQuest --> DMR[quest.DestinationMarkerRenderer]
	AutoVillager --> VillageInfo[model.VillageInfo]
```

### Runtime Modules

| Area | Main files | Responsibility |
| --- | --- | --- |
| Plugin entry point | `Plugin`, `plugin.yml` | Starts and stops the subplugins, registers the quest log listener. |
| Village maintenance | `AutoVillagerPlugin`, `VillageInfo` | Detects nearby village blocks and spawns villagers based on door count. |
| Dialogue system | `ConversationManager`, `ConversationState`, `ConversationPhase`, `DialogueGui`, `DialoguePrompts` | Manages NPC conversation flow, AI-driven dialogue, and inventory GUI screens. |
| Quest system | `RandomQuestPlugin`, `QuestManager`, `QuestProgress`, `Quest`, `QuestObjective`, `Npc` | Generates quests, tracks progress, handles completion, and grants rewards. |
| Quest log | `QuestLogBook`, `QuestLogGui`, `QuestLogListener` | Interactive quest log book item and GUI for viewing and abandoning quests. |
| Wandering peasants | `WanderingPeasantPlugin` | Spawns roaming quest NPCs using Wandering Trader entities. |
| Map rendering | `DestinationMarkerRenderer` | Draws destination markers on quest maps for `TREASURE` and `FIND_NPC` quests. |
| Quest generation | `QuestGenerationService` | Builds quests with random objectives and generates AI descriptions. |
| Utility | `EnumUtil` | Random enum value selection. |

## Quest Flow

```mermaid
sequenceDiagram
	actor Player
	participant NPC as Villager / Wandering Peasant
	participant ConvMgr as ConversationManager
	participant OpenAI as OpenAI chat model
	participant QuestManager
	participant Bukkit
	participant McMMO

	Player->>NPC: Right-click
	NPC->>ConvMgr: Start conversation
	ConvMgr->>OpenAI: Generate greeting
	OpenAI-->>ConvMgr: Greeting text
	ConvMgr->>Player: Show greeting GUI
	Player->>ConvMgr: Chat with NPC
	ConvMgr->>OpenAI: Generate casual chat (with quest hint if available)
	OpenAI-->>ConvMgr: Chat text hinting at trouble
	ConvMgr->>Player: Show chat with "Offer to help" button
	Player->>ConvMgr: Offer to help
	ConvMgr->>OpenAI: Generate quest narrative
	OpenAI-->>ConvMgr: Quest story
	ConvMgr->>Player: Show quest offer and accept/reject
	Player->>ConvMgr: Accept quest
	ConvMgr->>QuestManager: Assign quest + give quest log book
	QuestManager->>Bukkit: Create boss bars and progress task
	Player->>Bukkit: Kill, collect, or reach destination
	Bukkit->>QuestManager: Increment or complete quest
	QuestManager->>McMMO: Award raw XP
```

## Quest Log

Players receive a **Quest Log** book when they accept their first quest. The book serves as
an interactive quest tracker:

- **Right-click** the book to open a GUI showing all active quests with progress, time remaining, and objectives.
- **Click an abandon button** below any quest to cancel it (spawned entities and items are cleaned up).
- **Drop the book** to abandon all active quests — the book evaporates and all quest-related entities are removed.

## Village Scan Flow

```mermaid
flowchart LR
	PlayerTick[Scheduled player scan] --> Location[Player location]
	Location --> Blocks[Count doors and workstations]
	Blocks --> EnoughBlocks{Village blocks found?}
	EnoughBlocks -- no --> Stop[Do nothing]
	EnoughBlocks -- yes --> Spawn{Villagers below door-based target?}
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
