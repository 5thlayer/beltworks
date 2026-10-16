# Dismantle: a loaded line, and a pickaxe in a chest to take it up with, items and all.
function beltworks:gallery/clear
setblock ~5 ~ ~-7 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:gold_ingot",count:64},{Slot:3b,id:"minecraft:redstone",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:lapis_lazuli",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:gold_ingot",count:64},{Slot:9b,id:"minecraft:redstone",count:64},{Slot:10b,id:"minecraft:coal",count:64},{Slot:11b,id:"minecraft:lapis_lazuli",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:gold_ingot",count:64},{Slot:15b,id:"minecraft:redstone",count:64},{Slot:16b,id:"minecraft:coal",count:64},{Slot:17b,id:"minecraft:lapis_lazuli",count:64}]}
setblock ~6 ~ ~-7 beltworks:loader[facing=east]
fill ~7 ~ ~-7 ~20 ~ ~-7 beltworks:belt_tile[facing=east]
setblock ~21 ~ ~-7 beltworks:loader[facing=west]
setblock ~22 ~ ~-7 minecraft:chest[facing=north]
setblock ~13 ~ ~-2 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:iron_pickaxe",count:1}]}
function beltworks:gallery/sign {scene:"dismantle"}
