# Block Association Rework Plan

## Goal
Replace the current ID/suffix-based association logic with a deterministic, recipe-driven matrix build that:
- finds valid base blocks from registry state,
- attaches existing crafted variants to the correct base,
- generates missing Reshaped variants,
- keeps stable ordering,
- and **removes old name-based fallback logic completely**.

## Hard Requirement
- No legacy fallback: remove current suffix/name candidate logic in `BlockRegistryScanner` and `VariantCompleter`.
- If a relation cannot be proven by rules, do not auto-associate it.

## Why This Rework
Current logic relies on string patterns and can mis-associate blocks with unusual IDs (example: `minecraft:heavy_pressure_plate`).
Recipe- and state-based association is stricter and should reduce false positives.

## Current Pain Points (from code)
- `BlockRegistryScanner` currently resolves many variants by suffix stripping and candidate name guesses.
- `VariantCompleter` still finds slabs/stairs using path heuristics (`_block`, `_planks`, plural handling, etc.).
- Matrix refresh/order is fine, but grouping input is noisy because associations are inferred from IDs.

## Proposed Architecture
Use a staged pipeline with explicit passes:

1. **Base Candidate Pass**
- Iterate `Registries.BLOCK`.
- Keep blocks that pass `BaseBlockFilter`:
  - not `AIR`,
  - has item form,
  - full-cube default state (`isFullCube` / solid collision checks),
  - not a block entity provider,
  - not ignored namespaces/classes (copycat filter remains).
- Create one empty family per base candidate.

2. **Recipe Index Pass**
- Build a recipe index from `RecipeManager` (on datapack reload and server start).
- Parse `ShapedRecipe` + `ShapelessRecipe` first.
- For each recipe output block, extract all ingredient block candidates (expand tags).
- Determine if output is a variant of a single base candidate by recipe signature:
  - slab-like: 3x base in a row -> 6 output,
  - stairs-like: stair shape of one base,
  - wall/fence/pane/door/trapdoor/button/pressure-plate patterns (explicit signatures),
  - optionally stonecutting as a direct base -> variant relation.
- Store `output -> base` associations with confidence score and signature type.

3. **Family Build Pass**
- For each proven `output -> base` mapping:
  - add output to base family in `BlockMatrix`,
  - set reason with signature source (for debug tooltip),
  - reject ambiguous mappings (same confidence for >1 base).
- Ensure one block belongs to only one family.

4. **Generated Variant Pass**
- Run `VariantRegistry.registerAll(base, matrix)` for each base family.
- Keep current generated variants (vertical slab, vertical stairs, step, corner, vertical step).
- This pass only generates missing Reshaped variants; it does not infer vanilla/mod variants.

5. **Finalize Pass**
- `matrix.refresh()` once at end of full rebuild.
- Stable sort by block ID for base and variants (current behavior is acceptable).

## Implementation Steps (Concrete)

1. Introduce new services
- Add `BaseBlockFilter` (state-based base eligibility).
- Add `RecipeAssociationService` (recipe parsing + `output -> base` inference).
- Add `MatrixRebuilder` orchestrating full rebuild passes.

2. Replace scanner flow
- Replace current `BlockRegistryScanner.processBlock` per-block suffix inference with full rebuild trigger flow.
- Keep reactive updates, but route them through `MatrixRebuilder.rebuild()` instead of per-block heuristic association.

3. Remove legacy heuristic code
- Delete `buildCandidates`, `findBaseCandidate`, and suffix list logic from `BlockRegistryScanner`.
- Delete naming heuristic search in `VariantCompleter.findExistingVariant(...)`.
- Any existing-variant adoption must come from recipe association results.

4. Matrix API adjustments
- Add methods for controlled rebuild:
  - `clear()`,
  - `beginBatch()` / `endBatch()` or enforce single final refresh.
- Preserve `reasons`, `variantToBase`, and `columnByBlock` rebuild behavior.

5. Lifecycle wiring
- Rebuild on:
  - initial server ready event (recipes loaded),
  - datapack/resource reload event,
  - registry additions only if needed for late-registered blocks.
- Avoid building from incomplete recipe state.

6. Diagnostics
- Add debug logging counters:
  - base candidates found,
  - recipe associations accepted/rejected,
  - generated variants created,
  - ambiguous outputs skipped.

## Conflict Resolution Rules
- Prefer exact single-base recipe matches over heuristic guesses.
- If output recipe includes multiple different base candidates, skip association.
- If multiple bases tie with equal confidence, skip and log ambiguity.
- No implicit ID-based tie breaker.

## Testing Plan
- Add focused tests for:
  - vanilla slabs/stairs/walls/fences/trapdoors/doors/buttons/pressure plates,
  - weird IDs (ensure no false match by name),
  - modded blocks with tag ingredients,
  - ambiguous recipe outputs (should skip).
- Manual check with `/place_matrix`:
  - no obvious wrong columns,
  - stable ordering between runs,
  - generated variants still present.

## Rollout Plan
1. Merge architecture + new services behind one new pipeline.
2. Remove old logic in same branch (no fallback path).
3. Validate on vanilla + one large content mod set.
4. Ship with debug logging enabled for first test builds.

## Notes on Your Original Plan
- Strong direction: replacing ID matching is the right decision.
- Needs tightening: "full cube + no block entity" alone is not enough; recipe timing and ambiguity handling are critical.
- Biggest technical risk: recipe availability lifecycle. Build only when `RecipeManager` is ready.
