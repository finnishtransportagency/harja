(ns harja.domain.tilannekuva
  #?(:cljs
     (:require-macros [harja.domain.tilannekuva.makrot
                       :refer [maarittele-suodattimet]])

     :clj
     (:require [harja.domain.tilannekuva.makrot
                :refer [maarittele-suodattimet]])))


(defrecord Suodatin [id nimi otsikko])
(def suodatin? (partial instance? Suodatin))

(maarittele-suodattimet
 [laatupoikkeamat :laatupoikkeamat "Laatupoikkeamat"]
 [laatupoikkeama-tilaaja :tilaaja "Tilaaja"]
 [laatupoikkeama-urakoitsija :urakoitsija                      "Urakoitsija"]
 [laatupoikkeama-konsultti :konsultti                        "Konsultti"]

 [tarkastukset :tarkastukset                     "Tarkastukset"]
 [tarkastus-tiesto :tiesto                           "Tiestö"]
 [tarkastus-talvihoito :talvihoito                       "Talvihoito"]
 [tarkastus-soratie :soratie                          "Soratie"]
 [tarkastus-laatu :laatu                            "Laatu"]
 [tarkastus-pistokoe :pistokoe                         "Pistokoe"]
 [turvallisuuspoikkeamat :turvallisuuspoikkeamat "Turvallisuuspoikkeamat"]

 [tpp :toimenpidepyynto                 "TPP"]
 [tur :tiedoitus                        "TUR"]
 [urk :kysely                           "URK"]

 [paallystys :paallystys "Päällystystyöt"]
 [paikkaus :paikkaus "Paikkaustyöt"]

 [auraus-ja-sohjonpoisto     "auraus ja sohjonpoisto" "Auraus ja sohjonpoisto"]
 [suolaus                    "suolaus"                "Suolaus"]
 [pistehiekoitus             "pistehiekoitus"         "Pistehiekoitus"]
 [linjahiekoitus             "linjahiekoitus"         "Linjahiekoitus"]
 [pinnan-tasaus              "pinnan tasaus"          "Pinnan tasaus"]
 [liikennemerkkien-puhdistus "liikennemerkkien puhdistus"
  "Liikennemerkkien puhdistus"]
 [lumivallien-madaltaminen   "lumivallien madaltaminen"
  "Lumivallien madaltaminen"]
 [sulamisveden-haittojen-torjunta "sulamisveden haittojen torjunta"
  "Sulamisveden haittojen torjunta"]
 [tiestotarkastus "tiestotarkastus" "Tiestötarkastus"]
 [kelintarkastus "kelintarkastus" "Kelintarkastus"]
 [harjaus "harjaus" "Harjaus"]
 [koneellinen-niitto "koneellinen niitto" "Koneellinen niitto"]
 [koneellinen-vesakonraivaus "koneellinen vesakonraivaus"
  "Koneellinen vesakonraivaus"]
 [sorateiden-muokkaushoylays "sorateiden muokkaushoylays"
  "Sorateiden muokkaushöyläys"]
 [sorateiden-polynsidonta "sorateiden polynsidonta" "Sorateiden pölynsidonta"]
 [sorateiden-tasaus "sorateiden tasaus" "Sorateiden tasaus"]
 [sorastus "sorastus" "Sorastus"]
 [paallysteiden-paikkaus "paallysteiden paikkaus" "Päällysteiden paikkaus"]
 [paallysteiden-juotostyot "paallysteiden juotostyot"
  "Päällysteiden juotostyöt"]
 [siltojen-puhdistus "siltojen puhdistus" "Siltojen puhdistus"]
 [l-ja-p-alueiden-puhdistus "l- ja p-alueiden puhdistus"
  "L- ja P-alueiden puhdistus"]
 [muu "muu" "Muu"]
 [liuossuolaus "liuossuolaus" "Liuossuolaus"]
 [aurausviitoitus-ja-kinostimet "aurausviitoitus ja kinostimet"
  "Aurausviitoitus ja kinostimet"]
 [lumensiirto "lumensiirto" "Lumensiirto"]
 [paannejaan-poisto "paannejaan poisto" "Paannejään poisto"])

(def jarjestys
  {:talvi [auraus-ja-sohjonpoisto
           suolaus
           liuossuolaus
           pistehiekoitus
           linjahiekoitus
           pinnan-tasaus
           lumivallien-madaltaminen
           sulamisveden-haittojen-torjunta
           aurausviitoitus-ja-kinostimet
           lumensiirto
           paannejaan-poisto
           muu]
   :kesa  [sorateiden-polynsidonta
           sorastus
           sorateiden-tasaus
           sorateiden-muokkaushoylays
           paallysteiden-paikkaus
           paallysteiden-juotostyot
           koneellinen-niitto
           koneellinen-vesakonraivaus
           harjaus
           pinnan-tasaus
           liikennemerkkien-puhdistus
           l-ja-p-alueiden-puhdistus
           siltojen-puhdistus
           muu]})


(defn valitut-suodattimet
  "Ottaa nested map rakenteen, jossa viimeisellä tasolla avaimet ovat
Suodatin recordeja ja arvot boolean. Palauttaa mäpin muuten samalla rakenteella,
mutta viimeisen tason {suodatin boolean} mäpit on korvattu valittujen
suodattimien id numeroilla."
  [valinnat]
  (loop [m valinnat
         [[avain arvo] & loput] (seq valinnat)]
    (cond
      (nil? avain)
      m

      (and (map? arvo)
           (every? suodatin? (keys arvo)))
      ;; Seuraavalla tasolla on suodattimia, ota vain valitut
      (let [valitut (into #{}
                          (keep (fn [[suodatin valittu?]]
                                  (when valittu?
                                    (:id suodatin))))
                          (seq arvo))]
        (if (empty? valitut)
          (recur (dissoc m avain) loput)
          (recur (assoc m avain valitut)
                 loput)))

      (map? arvo)
      (recur (assoc m avain (valitut-suodattimet arvo)) loput)

      :else
      (recur m loput))))

(defn valittu? [valitut-set suodatin]
  (and valitut-set
       (valitut-set (:id suodatin))))
