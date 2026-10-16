# Slopes, and crossing over a line: a line running north crosses under one running east, which climbs a block
# to pass over it and comes down again on the far side.
function beltworks:gallery/clear
setblock ~13 ~ ~-11 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:gold_ingot",count:64},{Slot:3b,id:"minecraft:redstone",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:lapis_lazuli",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:gold_ingot",count:64},{Slot:9b,id:"minecraft:redstone",count:64},{Slot:10b,id:"minecraft:coal",count:64},{Slot:11b,id:"minecraft:lapis_lazuli",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:gold_ingot",count:64},{Slot:15b,id:"minecraft:redstone",count:64},{Slot:16b,id:"minecraft:coal",count:64},{Slot:17b,id:"minecraft:lapis_lazuli",count:64}]}
setblock ~13 ~ ~-10 beltworks:loader[facing=south]
fill ~13 ~ ~-9 ~13 ~ ~-3 beltworks:belt_tile[facing=south]
setblock ~13 ~ ~-2 beltworks:loader[facing=north]
setblock ~13 ~ ~-1 minecraft:chest[facing=north]
setblock ~5 ~ ~-6 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:gold_ingot",count:64},{Slot:3b,id:"minecraft:redstone",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:lapis_lazuli",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:gold_ingot",count:64},{Slot:9b,id:"minecraft:redstone",count:64},{Slot:10b,id:"minecraft:coal",count:64},{Slot:11b,id:"minecraft:lapis_lazuli",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:gold_ingot",count:64},{Slot:15b,id:"minecraft:redstone",count:64},{Slot:16b,id:"minecraft:coal",count:64},{Slot:17b,id:"minecraft:lapis_lazuli",count:64}]}
setblock ~6 ~ ~-6 beltworks:loader[facing=east]
# A tile climbs only where it is set to (ADR 0017): ~10 up to the raised stretch, ~15 down off it.
fill ~7 ~ ~-6 ~9 ~ ~-6 beltworks:belt_tile[facing=east]
setblock ~10 ~ ~-6 beltworks:belt_tile[facing=east,climb=up]
fill ~11 ~1 ~-6 ~14 ~1 ~-6 beltworks:belt_tile[facing=east]
setblock ~15 ~1 ~-6 beltworks:belt_tile[facing=east,climb=down]
fill ~16 ~ ~-6 ~20 ~ ~-6 beltworks:belt_tile[facing=east]
setblock ~21 ~ ~-6 beltworks:loader[facing=west]
setblock ~22 ~ ~-6 minecraft:chest[facing=north]
function beltworks:gallery/sign {scene:"slopes"}
