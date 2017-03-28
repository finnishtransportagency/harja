(ns harja.domain.paallystysilmoitus
  "Ylläpidon päällystysurakoissa käytettävän POT-lomakkeen skeemat."
  (:require [schema.core :as s]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]))

(def +tyomenetelmat+
  "Kaikki työmenetelmät POT-lomake Excelistä"
  [{:nimi "Paksuudeltaan vakio laatta" :lyhenne "LTA" :koodi 12}
   {:nimi "Massapintaus" :lyhenne "MP" :koodi 21}
   {:nimi "Kuumennuspintaus" :lyhenne "MPK" :koodi 22}
   {:nimi "MP kuumalle, kuumajyrsitylle tas. pinnalle" :lyhenne "MPKJ" :koodi 23}
   {:nimi "REMIX-pintaus" :lyhenne "REM" :koodi 31}
   {:nimi "2-kerroksinen remix-pintaus" :lyhenne "REM+" :koodi 32}
   {:nimi "PAB-O/V:n remix-pintaus" :lyhenne "REMO" :koodi 33}
   {:nimi "ART-pintaus" :lyhenne "ART" :koodi 34}
   {:nimi "Novachip-massapintaus" :lyhenne "NC" :koodi 35}
   {:nimi "Karhinta" :lyhenne "KAR" :koodi 41}
   {:nimi "Hienojyrsintä" :lyhenne "HJYR" :koodi 51}
   {:nimi "Sirotepintaus" :lyhenne "SIP" :koodi 61}
   {:nimi "Urapaikkaus" :lyhenne "UP" :koodi 71}
   {:nimi "Uraremix" :lyhenne "UREM" :koodi 72}])

(def +tyomenetelmat-ja-nil+
  (conj +tyomenetelmat+ {:nimi "Ei menetelmää" :lyhenne "Ei menetelmää" :koodi nil}))

(def +tyomenetelma-tai-nil+ "Työmenetelmän valinta koodilla"
  (apply s/enum (map :koodi +tyomenetelmat-ja-nil+)))

(defn tyomenetelman-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +tyomenetelmat-ja-nil+))))

(def +alustamenetelmat+
  "Kaikki alustan käsittelymenetelmät POT-lomake Excelistä"
  [{:nimi "Massanvaihto" :lyhenne "MV" :koodi 1}
   {:nimi "Bitumiemusiostabilointi" :lyhenne "BEST" :koodi 11}
   {:nimi "Vaahtobitumistabilointi" :lyhenne "VBST" :koodi 12}
   {:nimi "Remix-stabilointi" :lyhenne "REST" :koodi 13}
   {:nimi "Sementtistabilointi" :lyhenne "SST" :koodi 14}
   {:nimi "Masuunihiekkastabilointi" :lyhenne "MHST" :koodi 15}
   {:nimi "Komposiittistabilointi" :lyhenne "KOST" :koodi 16}
   {:nimi "Kantavan kerroksen AB" :lyhenne "ABK" :koodi 21}
   {:nimi "Sidekerroksen AB" :lyhenne "ABS" :koodi 22}
   {:nimi "Murske" :lyhenne "MS" :koodi 23}
   {:nimi "Kuumennustasaus" :lyhenne "TASK" :koodi 31}
   {:nimi "Massatasaus" :lyhenne "TAS" :koodi 32}
   {:nimi "Tasausjyrsintä" :lyhenne "TJYR" :koodi 41}
   {:nimi "Laatikkojyrsintä" :lyhenne "LJYR" :koodi 42}
   {:nimi "Reunajyrsintä" :lyhenne "RJYR" :koodi 43}])

(def +alustamenetelmat-ja-nil+
  (conj +alustamenetelmat+ {:nimi "Ei menetelmää" :lyhenne "Ei menetelmää" :koodi nil}))

(def +alustamenetelma+ "Alustan käsittelymenetelmän valinta koodilla"
  (apply s/enum (map :koodi +alustamenetelmat-ja-nil+)))

(defn alustamenetelma-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +alustamenetelmat-ja-nil+))))

(def +kuulamyllyt+
  [{:nimi "AN5" :koodi 1}
   {:nimi "AN7" :koodi 2}
   {:nimi "N10" :koodi 3}
   {:nimi "N14" :koodi 4}
   {:nimi "N19" :koodi 5}
   {:nimi "N30" :koodi 6}
   {:nimi "N22" :koodi 7}])

(def +kyylamyllyt-ja-nil+
  (conj +kuulamyllyt+ {:nimi "Ei kuulamyllyä" :koodi nil}))

(def +kuulamylly+ "Kuulamylly annetulla koodilla"
  (apply s/enum (map :koodi +kyylamyllyt-ja-nil+)))

(defn kuulamylly-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +kyylamyllyt-ja-nil+))))

(def +verkkotyypit+
  "Verkkotyypit POT-lomake Excelistä"
  [{:nimi "Teräsverkko" :koodi 1}
   {:nimi "Lasikuituverkko" :koodi 2}
   {:nimi "Muu" :koodi 9}])

(def +verkkotyypit-ja-nil+
  (conj +verkkotyypit+ {:nimi "Ei verkkotyyppiä" :koodi nil}))

(def +verkkotyyppi-tai-nil+ "Verkkotyypin valinta koodilla"
  (apply s/enum (map :koodi +verkkotyypit-ja-nil+)))

(defn verkkotyyppi-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +verkkotyypit-ja-nil+))))

(def +tekniset-toimenpiteet+
  "Tekniset toimenpidetyypit POT-lomake Excelistä"
  [{:nimi "Rakentaminen" :koodi 1}
   {:nimi "Suuntauksen parantaminen" :koodi 2}
   {:nimi "Raskas rakenteen parantaminen" :koodi 3}
   {:nimi "Kevyt rakenteen parantaminen" :koodi 4}])

(def +tekniset-toimenpiteet-ja-nil+
  (conj +tekniset-toimenpiteet+ {:nimi "Ei toimenpidettä" :koodi nil}))

(def +tekninen-toimenpide-tai-nil+ "Teknisen toimenpiteen valinta koodilla"
  (apply s/enum (map :koodi +tekniset-toimenpiteet-ja-nil+)))

(defn tekninentoimenpide-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +tekniset-toimenpiteet-ja-nil+))))

(def +ajoradat+
  "Ajoratavalinnat"
  [{:nimi "Yksiajoratainen" :koodi 0}
   {:nimi "Kaksiajorataisen ensimmäinen" :koodi 1}
   {:nimi "Kaksiajorataisen toinen ajorata" :koodi 2}])

(def +kaistat+
  "Kaistavalinnat"
  [{:nimi "1" :koodi 1}
   {:nimi "11" :koodi 11}
   {:nimi "12" :koodi 12}
   {:nimi "13" :koodi 13}
   {:nimi "14" :koodi 14}
   {:nimi "15" :koodi 15}
   {:nimi "16" :koodi 16}
   {:nimi "17" :koodi 17}
   {:nimi "18" :koodi 18}
   {:nimi "19" :koodi 19}
   {:nimi "21" :koodi 21}
   {:nimi "22" :koodi 22}
   {:nimi "23" :koodi 23}
   {:nimi "24" :koodi 24}
   {:nimi "25" :koodi 25}
   {:nimi "26" :koodi 26}
   {:nimi "27" :koodi 27}
   {:nimi "28" :koodi 28}
   {:nimi "29" :koodi 29}])

(def +sideainetyypit+
  "Sideainetyypit"
  [{:nimi "20/30" :koodi 1}
   {:nimi "35/50" :koodi 2}
   {:nimi "50/70" :koodi 3}
   {:nimi "70/100" :koodi 4}
   {:nimi "100/150" :koodi 5}
   {:nimi "160/220" :koodi 6}
   {:nimi "250/330" :koodi 7}
   {:nimi "330/430" :koodi 8}
   {:nimi "500/650" :koodi 9}
   {:nimi "650/900" :koodi 10}
   {:nimi "V1500" :koodi 11}
   {:nimi "V3000" :koodi 12}
   {:nimi "KB65" :koodi 13}
   {:nimi "KB75" :koodi 14}
   {:nimi "KB85" :koodi 15}
   {:nimi "BL5" :koodi 16}
   {:nimi "BL2K" :koodi 17}
   {:nimi "BL2 Bio" :koodi 18}
   {:nimi "BE-L" :koodi 19}
   {:nimi "BE-SIP" :koodi 20}
   {:nimi "BE-SOP" :koodi 21}
   {:nimi "BE-PAB" :koodi 22}])

(def +sideainetyypit-ja-nil+
  (conj +sideainetyypit+ {:nimi "Ei sideainetyyppi" :lyhenne "Ei sideainetyyppiä" :koodi nil}))

(def +sideainetyyppi-tai-nil+
  "Sideainetyypin valinta koodilla"
  (apply s/enum (map :koodi +sideainetyypit-ja-nil+)))

(defn sideainetyypin-koodi-nimella [nimi]
  (:koodi (first (filter #(= nimi (:nimi %)) +sideainetyypit-ja-nil+))))

(def +verkon-tarkoitukset+
  [{:nimi "Pituushalkeamien ehkäisy" :koodi 1}
   {:nimi "Muiden routavaurioiden ehkäisy" :koodi 2}
   {:nimi "Levennyksen tukeminen" :koodi 3}
   {:nimi "Painumien ehkäisy" :koodi 4}
   {:nimi "Moniongelmaisen tukeminen" :koodi 5}
   {:nimi "Muu tarkoitus" :koodi 9}])

(def +verkon-tarkoitukset-ja-nil+
  (conj +verkon-tarkoitukset+ {:nimi "Ei tarkoitusta" :koodi nil}))

(def +verkon-tarkoitus-tai-nil+
  "Verkon tarkoituksen valinta koodilla"
  (apply s/enum (map :koodi +verkon-tarkoitukset-ja-nil+)))

(defn verkon-tarkoitus-koodi-nimella [koodi]
  (:koodi (first (filter #(= koodi (:nimi %)) +verkon-tarkoitukset-ja-nil+))))

(def +verkon-sijainnit+
  [{:nimi "Päällysteessä" :koodi 1}
   {:nimi "Kantavan kerroksen yläpinnassa" :koodi 2}
   {:nimi "Kantavassa kerroksessa" :koodi 3}
   {:nimi "Kantavan kerroksen alapinnassa" :koodi 4}
   {:nimi "Muu sijainti" :koodi 9}
   {:nimi "Ei sijaintia" :koodi nil}])

(def +verkon-sijainnit-ja-nil+
  (conj +verkon-sijainnit+ {:nimi "Ei sijaintia" :koodi nil}))

(def +verkon-sijainti-tai-nil+
  "Verkon sijainnin valinta koodilla"
  (apply s/enum (map :koodi +verkon-sijainnit-ja-nil+)))

(defn verkon-sijainti-koodi-nimella [koodi]
  (:koodi (first (filter #(= koodi (:nimi %)) +verkon-sijainnit-ja-nil+))))

(def +paallystystyon-tyypit+
  "Päällystystyön tyypit"
  [{:nimi "Ajoradan päällyste" :koodi :ajoradan-paallyste}
   {:nimi "Pienaluetyöt" :koodi :pienaluetyot}
   {:nimi "Tasaukset" :koodi :tasaukset}
   {:nimi "Jyrsinnät" :koodi :jyrsinnat}
   {:nimi "Muut" :koodi :muut}])

(defn paallystystyon-tyypin-nimi-koodilla [koodi]
  (:nimi (first (filter
                  #(= koodi (:koodi %))
                  +paallystystyon-tyypit+))))

(def paallystysilmoitus-osoitteet ;; Kantaan tallennettava päällystysilmoitus
  [;; Linkki ylläpitokohdeosaan
   {:kohdeosa-id s/Int

    ; Osoitteelle tehdyt toimenpiteet
    (s/optional-key :paallystetyyppi) paallystys-ja-paikkaus/+paallystetyyppi-tai-nil+
    (s/optional-key :raekoko) (s/maybe s/Int)
    (s/optional-key :massamenekki) (s/maybe s/Int) ;; kg/m2
    (s/optional-key :rc%) (s/maybe s/Int)
    (s/optional-key :tyomenetelma) +tyomenetelma-tai-nil+
    (s/optional-key :leveys) (s/maybe s/Num) ;; metriä
    (s/optional-key :kokonaismassamaara) (s/maybe s/Num) ;; tonnia
    (s/optional-key :pinta-ala) (s/maybe s/Num) ;; m2
    (s/optional-key :kuulamylly) (s/maybe +kuulamylly+)
    ;; Edellinen päällystetyyppi -arvoa käytettiin lomakkeessa aiemmin, nykyään ei ole enää kiinnostava tieto
    ;; Säilytetään skeemassa vanhan datan yhteensopivuuden vuoksi
    (s/optional-key :edellinen-paallystetyyppi) paallystys-ja-paikkaus/+paallystetyyppi-tai-nil+

    ;; N kpl kiviainesesiintymiä
    (s/optional-key :esiintyma) (s/maybe s/Str)
    (s/optional-key :km-arvo) (s/maybe s/Str)
    (s/optional-key :muotoarvo) (s/maybe s/Str)
    (s/optional-key :sideainetyyppi) +sideainetyyppi-tai-nil+
    (s/optional-key :pitoisuus) (s/maybe s/Num)
    (s/optional-key :lisaaineet) (s/maybe s/Str)}])

(def paallystysilmoitus-alustatoimet
  [{:tr-alkuosa s/Int
    :tr-alkuetaisyys s/Int
    :tr-loppuosa s/Int
    :tr-loppuetaisyys s/Int
    :kasittelymenetelma +alustamenetelma+
    :paksuus s/Int ;; cm
    (s/optional-key :verkkotyyppi) +verkkotyyppi-tai-nil+
    (s/optional-key :verkon-tarkoitus) +verkon-tarkoitus-tai-nil+
    (s/optional-key :verkon-sijainti) +verkon-sijainti-tai-nil+
    (s/optional-key :tekninen-toimenpide) +tekninen-toimenpide-tai-nil+}])

;; Kantaan tallennettavan päällystysilmoituksen ilmoitustiedot
(def +paallystysilmoitus+
  {;; Toteutuneet osoitteet. Esitäytetään kohdeluettelon kohdeosilla, mutta voi muokata käsin.
   :osoitteet paallystysilmoitus-osoitteet

   ;; Tieosoitteille tehtyjä toimia, mutta ei esitäytetä osoitteita, voi olla monta samalle
   ;; kohdallekin. Vaihtelee alustan laadun mukaan (esim. löytyy kiviä).
   ;; Välien tulee olla kohdeluettelon osoitteiden sisällä.
   :alustatoimet paallystysilmoitus-alustatoimet})

(defn paattele-ilmoituksen-tila
  [valmis-kasiteltavaksi tekninen-osa-hyvaksytty]
  (cond
    tekninen-osa-hyvaksytty
    "lukittu"

    valmis-kasiteltavaksi
    "valmis"

    :default
    "aloitettu"))