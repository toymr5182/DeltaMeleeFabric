# DeltaForce Melee Models (Fabric, MC 26.3)

Rename a **diamond** or **netherite sword** in an anvil to one of these names and it renders as that model
(exact match, case-sensitive; the lowercase ids also work):

| Anvil name | Model | Anvil name | Model |
|---|---|---|---|
| Mercy | mercy | Sea Gleam | thieve_sea |
| Fiery Owl | shadowkiller | Cobra Vanguard | cobra_vanguard |
| Desmoulins' Edge | polaris | Creed | treasure_kn |
| TAC Dagger | silencefd | Dragon's Fang | quenching |
| Dark Star | annihil | Executor | executor |
| "Free Dark Star" (or Free Dark Star) | null_annihil | | |

Rename to anything else (or remove the name) and you get the normal sword. Client-side only; no Fabric API needed.

## Build (Colab / any Linux)
```
# needs JDK 25 + Gradle 9.x
gradle build
# result: build/libs/delta-melee-fabric-0.1.0.jar  -> put in .minecraft/mods
```
If the Loom plugin id or version is rejected, use the ones from https://fabricmc.net/develop (26.3) in `build.gradle`/`gradle.properties`.

## Known limits
- Models are static (TACZ animations/Lua state machines are not used).
- Orientation/size are auto-fitted; tune `GeoModel.NEGATE_XY_ROT` or pass an orientation if a model looks mirrored/tilted.
