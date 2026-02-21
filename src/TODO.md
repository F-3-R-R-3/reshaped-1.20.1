# todo

- [ ] make vertical stairs connect with normal stairs
- [ ] add panel blocks
- [x] fix transparent blocks
- [ ] auto change recipes
- [ ] fix pick-block for mixed blocks
- [x] fix vertical slab placement

## performance

- [ ] avoid registering static per-state models for mixed blocks; use a lightweight placeholder model/state path where possible
- [ ] cache composed quads for repeated mixed state/material combinations
- [ ] reduce `context.addModels(...)` pre-registration for variants that are not used directly
