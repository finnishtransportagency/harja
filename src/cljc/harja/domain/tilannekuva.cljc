(ns harja.domain.tilannekuva
  #?(:cljs
     (:require-macros [harja.domain.tilannekuva.makrot
                       :refer [maarittele-suodattimet]])

     :clj
     (:require [harja.domain.tilannekuva.makrot
                :refer [maarittele-suodattimet]])))


(defrecord Suodatin [id nimi otsikko])
(defrecord Aluesuodatin [id nimi otsikko alue])
(defn suodatin? [s] (or (instance? Suodatin s)
                        (instance? Aluesuodatin s)))

(maarittele-suodattimet
  [laatupoikkeamat :laatupoikkeamat "Laatupoikkeamat"]
  [laatupoikkeama-tilaaja :tilaaja "Tilaaja"]
  [laatupoikkeama-urakoitsija :urakoitsija "Urakoitsija"]
  [laatupoikkeama-konsultti :konsultti "Konsultti"]

  [tarkastukset :tarkastukset "Tarkastukset"]
  [tarkastus-tiesto :tiesto "Tiestö"]
  [tarkastus-talvihoito :talvihoito "Talvihoito"]
  [tarkastus-soratie :soratie "Soratie"]
  [tarkastus-laatu :laatu "Laatu"]
  [turvallisuuspoikkeamat :turvallisuuspoikkeamat "Turvallisuuspoikkeamat"]

  [tpp :toimenpidepyynto "TPP"]
  [tur :tiedoitus "TUR"]
  [urk :kysely "URK"]

  [paallystys :paallystys "Päällystystyöt"]
  [paikkaus :paikkaus "Paikkaustyöt"]
  [tietyomaat :tietyomaat "Tietyömaat"]
  [paaasfalttilevitin "asfaltointi" "Pääasfalttilevittimet"]
  [tiemerkintakone "tiemerkinta" "Tiemerkintäkoneet"]
  [kuumennuslaite "kuumennus" "Kuumennuslaitteet"]
  [sekoitus-ja-stabilointijyrsin "sekoitus tai stabilointi" "Sekoitus- ja stabilointijyrsimet"]
  [tma-laite "turvalaite" "TMA-laitteet"]
  [jyra "jyrays" "Jyrät"]

  [auraus-ja-sohjonpoisto "auraus ja sohjonpoisto" "Auraus ja sohjonpoisto"]
  [suolaus "suolaus" "Suolaus"]
  [pistehiekoitus "pistehiekoitus" "Pistehiekoitus"]
  [linjahiekoitus "linjahiekoitus" "Linjahiekoitus"]
  [pinnan-tasaus "pinnan tasaus" "Pinnan tasaus"]
  [liikennemerkkien-puhdistus "liikennemerkkien puhdistus"
   "Liikennemerkkien puhdistus"]
  [liikennemerkkien-opasteiden-ja-liikenteenohjauslaitteiden-hoito-seka-reunapaalujen-kunnossapito
   "liik. opast. ja ohjausl. hoito seka reunapaalujen kun.pito"
   "Liikennemerkkien, opasteiden ja liikenteenohjauslaitteiden hoito sekä reunapaalujen kunnossapito"]
  [palteen-poisto "palteen poisto" "Palteen poisto"]
  [paallystetyn-tien-sorapientareen-taytto
   "paallystetyn tien sorapientareen taytto"
   "Päällystetyn tien sorapientareen täyttö"]
  [ojitus "ojitus" "Ojitus"]
  [sorapientareen-taytto "sorapientareen taytto" "Sorapientareen täyttö"]
  [lumivallien-madaltaminen "lumivallien madaltaminen" "Lumivallien madaltaminen"]
  [sulamisveden-haittojen-torjunta "sulamisveden haittojen torjunta" "Sulamisveden haittojen torjunta"]
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
  ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
  ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
  ;; kokee tietävänsä asian varmaksi.
  ;; [liuossuolaus "liuossuolaus" "Liuossuolaus"]
  [aurausviitoitus-ja-kinostimet "aurausviitoitus ja kinostimet"
   "Aurausviitoitus ja kinostimet"]
  [lumensiirto "lumensiirto" "Lumensiirto"]
  [paannejaan-poisto "paannejaan poisto" "Paannejään poisto"])

(def tehtavien-jarjestys
  {:ilmoitukset {:tyypit [tpp tur urk]}
   :yllapito [paallystys
              paikkaus
              tietyomaat
              paaasfalttilevitin
              tiemerkintakone
              kuumennuslaite
              jyra
              sekoitus-ja-stabilointijyrsin
              tma-laite]
   :yllapidon-reaaliaikaseuranta []
   :talvi [auraus-ja-sohjonpoisto
           suolaus
           ;; Liuossuolausta ei ymmärtääkseni enää seurata, mutta kesälomien takia tässä on korjauksen
           ;; hetkellä pieni informaatiouupelo. Nämä rivit voi poistaa tulevaisuudessa, jos lukija
           ;; kokee tietävänsä asian varmaksi.
           ;;liuossuolaus
           pistehiekoitus
           linjahiekoitus
           pinnan-tasaus
           lumivallien-madaltaminen
           sulamisveden-haittojen-torjunta
           aurausviitoitus-ja-kinostimet
           lumensiirto
           paannejaan-poisto
           muu]
   :kesa [;; Näitä kolmea ei haluta nähdä tilannekuvassa, mutta tuki niiden näyttämiselle on olemassa
          ;;koneellinen-niitto
          ;;koneellinen-vesakonraivaus
          ;;liikennemerkkien-puhdistus
          sorapientareen-taytto
          sorateiden-polynsidonta
          sorastus
          sorateiden-tasaus
          sorateiden-muokkaushoylays
          paallysteiden-paikkaus
          paallysteiden-juotostyot
          koneellinen-niitto
          koneellinen-vesakonraivaus
          harjaus
          liikennemerkkien-puhdistus
          l-ja-p-alueiden-puhdistus
          siltojen-puhdistus
          liikennemerkkien-opasteiden-ja-liikenteenohjauslaitteiden-hoito-seka-reunapaalujen-kunnossapito
          palteen-poisto
          paallystetyn-tien-sorapientareen-taytto
          ojitus
          muu]})

(def yllapidon-reaaliaikaseurattavat
  #{(:id paaasfalttilevitin)
    (:id tiemerkintakone)
    (:id jyra)
    (:id kuumennuslaite)
    (:id sekoitus-ja-stabilointijyrsin)
    (:id tma-laite)})

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

(defn suodatin-muutettuna
  "Ottaa nested map rakenteen, jossa viimeisellä tasolla avaimet ovat
  Suodatin recordeja ja arvot boolean. Lisäksi ottaa kahden parametrin funktion ja setin id:tä.
  Kun löydetään id-settiin osuva suodatin, kutsutaan funktiota suodattimella ja boolean-arvolla.
  Funktion paluu arvo korvaa vanhan [suodatin boolean] avain-arvoparin.
  Mäp palautetaan samalla rakenteella."
  [valinnat funktio id-set]
  (loop [m valinnat
         [[avain arvo] & loput] (seq valinnat)]
    (cond
      (nil? avain)
      m

      (and (map? arvo)
           (every? suodatin? (keys arvo)))
      (let [loytyy? (some #(id-set (:id %)) (keys arvo))
            tulos (if-not loytyy?
                    arvo
                    (into {}
                          (map
                            (fn [[suodatin valittu? :as pari]]
                              (if (id-set (:id suodatin)) (funktio suodatin valittu?) pari))
                            (seq arvo))))]
        (recur (assoc m avain tulos) loput))

      (map? arvo)
      (recur (assoc m avain (suodatin-muutettuna arvo funktio id-set)) loput)

      :else
      (recur m loput))))

(defn valittu? [valitut-set suodatin]
  (some?
    (and valitut-set
         (valitut-set (:id suodatin)))))

(defn- valitut-kentat* [taulukko suodattimet]
  (loop [t taulukko
         [[avain arvo] & loput] (seq suodattimet)]
    (cond
      (nil? avain)
      t

      (and (map? arvo) (every? suodatin? (keys arvo)))
      (recur (concat t (keep (fn [[suodatin valittu?]] (when valittu? suodatin)) (seq arvo))) loput)

      (map? arvo)
      (recur (valitut-kentat* t arvo) loput)

      :else
      (recur t loput))))

(defn valitut-kentat [valinnat]
  "Valitsee joukosta suodattimia valitut, ja palauttaa itse suodattimet listassa."
  (valitut-kentat* [] valinnat))

(defn valittujen-suodattimien-idt [valinnat]
  (let [valitut-kentat (valitut-kentat valinnat)]
    (into #{} (map :id valitut-kentat))))

(defn yllapidon-reaaliaikaseurattava? [id]
  (yllapidon-reaaliaikaseurattavat id))
