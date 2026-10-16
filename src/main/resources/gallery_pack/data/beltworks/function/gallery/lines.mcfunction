# Tiles merged into a transport line: a chest's loader feeds a line that runs east, turns two corners and
# runs back west to an end with nowhere to go, so the items back up and wait along it.
function beltworks:gallery/clear
setblock ~6 ~ ~-4 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:gold_ingot",count:64},{Slot:3b,id:"minecraft:redstone",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:lapis_lazuli",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:gold_ingot",count:64},{Slot:9b,id:"minecraft:redstone",count:64},{Slot:10b,id:"minecraft:coal",count:64},{Slot:11b,id:"minecraft:lapis_lazuli",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:gold_ingot",count:64},{Slot:15b,id:"minecraft:redstone",count:64},{Slot:16b,id:"minecraft:coal",count:64},{Slot:17b,id:"minecraft:lapis_lazuli",count:64}]}
setblock ~7 ~ ~-4 beltworks:loader[facing=east]
fill ~8 ~ ~-4 ~19 ~ ~-4 beltworks:belt_tile[facing=east]
fill ~20 ~ ~-4 ~20 ~ ~-7 beltworks:belt_tile[facing=north]
fill ~20 ~ ~-8 ~8 ~ ~-8 beltworks:belt_tile[facing=west]
function beltworks:gallery/sign {scene:"lines"}
