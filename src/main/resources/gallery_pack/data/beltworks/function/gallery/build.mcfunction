# The Beltworks Gallery: a scene of each README feature, in README order, the first where it is run and each
# further one 24 blocks east (libworks ADR 0010). Each clears its own plot first.
function beltworks:gallery/lines
execute positioned ~24 ~ ~ run function beltworks:gallery/stretch
execute positioned ~48 ~ ~ run function beltworks:gallery/slopes
execute positioned ~72 ~ ~ run function beltworks:gallery/tiers
execute positioned ~96 ~ ~ run function beltworks:gallery/splitters
execute positioned ~120 ~ ~ run function beltworks:gallery/filtered_splitter
execute positioned ~144 ~ ~ run function beltworks:gallery/loaders
execute positioned ~168 ~ ~ run function beltworks:gallery/filtered_loader
execute positioned ~192 ~ ~ run function beltworks:gallery/feeders
execute positioned ~216 ~ ~ run function beltworks:gallery/filtered_feeder
