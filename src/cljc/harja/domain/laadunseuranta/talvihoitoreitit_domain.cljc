(ns harja.domain.laadunseuranta.talvihoitoreitit-domain
  (:require [harja.tyokalut.yleiset :as yleiset-tyokalut]))

  (def talvihoitoreittien-varit
       ["#FF5733" "#FF8D1A" "#FFC300" "#FFD700" "#FFFF66" "#DFFF00" "#ADFF2F" "#7FFF00" "#32CD32" "#00FF7F"
        "#20B2AA" "#40E0D0" "#48D1CC" "#87CEEB" "#00BFFF" "#1E90FF" "#6495ED" "#7B68EE" "#9370DB" "#8A2BE2"
        "#9932CC" "#9400D3" "#8B008B" "#C71585" "#FF1493" "#FF69B4" "#FFB6C1" "#FFA07A" "#FF6347" "#FF4500"
        "#DC143C" "#CD5C5C" "#F08080" "#FA8072" "#E9967A" "#FFA07A" "#FFDAB9" "#FFE4B5" "#FFEBCD" "#F0E68C"])

(defn anna-random-vari [_]
  (let [varien-maara (dec (count talvihoitoreittien-varit))
        random-luku (yleiset-tyokalut/random-luku-valilta 0 varien-maara)]
    (nth talvihoitoreittien-varit random-luku)))
