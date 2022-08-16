(ns harja.domain.varuste-ulkoiset)

(def toteuma->toimenpide-map
  {"lisatty" "Lisätty"
   "paivitetty" "Päivitetty"
   "poistettu" "Poistettu"})

(defn toteuma->toimenpide [toteuma]
  (get
    toteuma->toimenpide-map
    toteuma
    toteuma))

(def tietolaji->varustetyyppi-map {"tl501" "Kaiteet"
                                   "tl503" "Levähdysalueiden varusteet"
                                   "tl504" "WC"
                                   "tl505" "Jätehuolto"
                                   "tl506" "Liikennemerkit"
                                   "tl507" "Bussipysäkin varusteet"
                                   "tl508" "Bussipysäkin katos"
                                   "tl516" "Hiekkalaatikot"
                                   "tl509" "Rummut"
                                   "tl512" "Viemärit"
                                   "tl513" "Reunapaalut"
                                   "tl514" "Melurakenteet"
                                   "tl515" "Aidat"
                                   "tl517" "Portaat"
                                   "tl518" "Kivetyt alueet"
                                   "tl520" "Puomit"
                                   "tl522" "Reunakivet"
                                   "tl524" "Viherkuviot"})

(defn tietolaji->varustetyyppi [tietolaji]
  (get
    tietolaji->varustetyyppi-map
    tietolaji
    (str "Tuntematon: " tietolaji)))

