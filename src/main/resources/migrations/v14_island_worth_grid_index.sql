-- v14: island_worth grid index (coalesced write hot path)
CREATE INDEX IF NOT EXISTS idx_island_worth_grid ON island_worth(grid_x, grid_z, dimension);