# One gesture lays a whole stretch: belt tiles in a chest, to lay one from the gold block to the emerald
# block, round the wall between them.
function beltworks:gallery/clear
setblock ~13 ~ ~-2 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"beltworks:belt_tile",count:64}]}
setblock ~6 ~-1 ~-7 minecraft:gold_block
setblock ~21 ~-1 ~-7 minecraft:emerald_block
fill ~13 ~ ~-8 ~13 ~1 ~-6 minecraft:stone_bricks
function beltworks:gallery/sign {scene:"stretch"}
