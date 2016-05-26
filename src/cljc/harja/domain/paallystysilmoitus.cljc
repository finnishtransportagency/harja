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

(def +tyomenetelma+ "Työmenetelmän valinta koodilla"
  (apply s/enum (map :koodi +tyomenetelmat+)))

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

(def +alustamenetelma+ "Alustan käsittelymenetelmän valinta koodilla"
  (apply s/enum (map :koodi +alustamenetelmat+)))

(def +kuulamyllyt+
  [{:nimi "AN5" :koodi 1}
   {:nimi "AN7" :koodi 2}
   {:nimi "N10" :koodi 3}
   {:nimi "N14" :koodi 4}
   {:nimi "N19" :koodi 5}
   {:nimi "N30" :koodi 6}
   {:nimi "N22" :koodi 7}])

(def +kuulamylly+ "Kuulamylly annetulla koodilla"
  (apply s/enum (map :koodi +kuulamyllyt+)))

(def +verkkotyypit+
  "Verkkotyypit POT-lomake Excelistä"
  [{:nimi "Teräsverkko" :koodi 1}
   {:nimi "Lasikuituverkko" :koodi 2}
   {:nimi "Muu" :koodi 9}])

(def +verkkotyyppi+ "Verkkotyypin valinta koodilla"
  (apply s/enum (map :koodi +verkkotyypit+)))

(def +tekniset-toimenpiteet+
  "Tekniset toimenpidetyypit POT-lomake Excelistä"
  [{:nimi "Rakentaminen" :koodi 1}
   {:nimi "Suuntauksen parantaminen" :koodi 2}
   {:nimi "Raskas rakenteen parantaminen" :koodi 3}])

(def +tekninen-toimenpide+ "Teknisen toimenpiteen valinta koodilla"
  (apply s/enum (map :koodi +tekniset-toimenpiteet+)))

(def +ajoradat+
  "Ajoratavalinnat"
  [{:nimi "Yksiajoratainen" :koodi 0}
   {:nimi "Kaksiajorataisen ensimmäinen" :koodi 1}
   {:nimi "Kaksiajorataisen toinen ajorata" :koodi 2}])

(def +ajorata+
  "Ajoratavalinta koodilla"
  (apply s/enum (map :koodi +ajoradat+)))

(def +suunnat+
  "Suuntavalinnat"
  [{:nimi "Molemmat suunnat samassa" :koodi 0}
   {:nimi "Tierekisterin suunta" :koodi 1}
   {:nimi "Tierekisterin vastainen suunta" :koodi 2}])

(def +suunta+
  "Suuntavalinta koodilla"
  (apply s/enum (map :koodi +suunnat+)))

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

(def +kaista+
  "Kaistavalinta koodilla"
  (apply s/enum (map :koodi +kaistat+)))

(def +verkon-tarkoitukset+
  [{:nimi "Pituushalkeamien ehkäisy" :koodi 1}
   {:nimi "Muiden routavaurioiden ehkäisy" :koodi 2}
   {:nimi "Levennyksen tukeminen" :koodi 3}
   {:nimi "Painumien ehkäisy" :koodi 4}
   {:nimi "Moniongelmaisen tukeminen" :koodi 5}
   {:nimi "Muu tarkoitus" :koodi 9}])

(def +verkon-tarkoitus+
  "Verkon tarkoituksen valinta koodilla"
  (apply s/enum (map :koodi +verkon-tarkoitukset+)))

(def +verkon-sijainnit+
  [{:nimi "Päällysteessä" :koodi 1}
   {:nimi "Kantavan kerroksen yläpinnassa" :koodi 2}
   {:nimi "Kantavassa kerroksessa" :koodi 3}
   {:nimi "Kantavan kerroksen alapinnassa" :koodi 4}
   {:nimi "Muu sijainti" :koodi 9}])

(def +verkon-sijainti+
  "Verkon sijainnin valinta koodilla"
  (apply s/enum (map :koodi +verkon-sijainnit+)))

(def +paallystystyon-tyypit+
  "Päällystystyön tyypit"
  [{:avain :ajoradan-paallyste :nimi "Ajoradan päällyste"}
   {:avain :pienaluetyot :nimi "Pienaluetyöt"}
   {:avain :tasaukset :nimi "Tasaukset"}
   {:avain :jyrsinnat :nimi "Jyrsinnät"}
   {:avain :muut :nimi "Muut"}])

(def +paallystystyon-tyyppi+
  "Päällystystyön valinta avaimella"
  (apply s/enum (map :avain +paallystystyon-tyypit+)))

(def +paallystysilmoitus+
  {;; Toteutuneet osoitteet. Esitäytetään kohdeluettelon kohdeosilla, mutta voi muokata käsin.
   :osoitteet
   [{(s/optional-key :nimi) s/Str
     :tie s/Int
     (s/optional-key :ajorata) (s/maybe +ajorata+)
     (s/optional-key :kaista) (s/maybe +kaista+)
     :aosa s/Int
     :aet s/Int
     :losa s/Int
     :let s/Int
     (s/optional-key :kohdeosa-id) (s/maybe s/Int)

     ; Osoitteelle tehdyt toimenpiteet
     (s/optional-key :paallystetyyppi) (s/maybe paallystys-ja-paikkaus/+paallystetyyppi+)
     (s/optional-key :raekoko) (s/maybe s/Int)
     (s/optional-key :massa) (s/maybe s/Num) ;; kg/m2
     (s/optional-key :rc%) (s/maybe s/Int)
     (s/optional-key :tyomenetelma) (s/maybe +tyomenetelma+) ;; koodisto "työmenetelmä"
     (s/optional-key :leveys) (s/maybe s/Num) ;; metriä, esim. 4,2
     (s/optional-key :massamaara) (s/maybe s/Num) ;; tonnia
     (s/optional-key :pinta-ala) (s/maybe s/Num) ;; m2
     (s/optional-key :kuulamylly) (s/maybe +kuulamylly+)
     (s/optional-key :edellinen-paallystetyyppi) (s/maybe paallystys-ja-paikkaus/+paallystetyyppi+)

     ;; N kpl kiviainesesiintymiä (ei liity osoitteiden järjestykseen)
     (s/optional-key :esiintyma) (s/maybe s/Str)
     (s/optional-key :km-arvo) (s/maybe s/Str)
     (s/optional-key :muotoarvo) (s/maybe s/Str)
     (s/optional-key :sideainetyyppi) (s/maybe s/Str)
     (s/optional-key :pitoisuus) (s/maybe s/Num)
     (s/optional-key :lisaaineet) (s/maybe s/Str)
     (s/optional-key :poistettu) s/Bool}]

  ;; Tieosoitteille tehtyjä toimia, mutta ei esitäytetä osoitteita, voi olla monta samalle
  ;; kohdallekin. Vaihtelee alustan laadun mukaan (esim. löytyy kiviä).
  ;; Välien tulee olla kohdeluettelon osoitteiden sisällä.
  :alustatoimet
  [{:aosa s/Int
    :aet s/Int
    :losa s/Int
    :let s/Int
    (s/optional-key :kasittelymenetelma) (s/maybe +alustamenetelma+) ;; +alustamenetelma+ skeemasta
    (s/optional-key :paksuus) (s/maybe s/Num) ;; cm
    (s/optional-key :verkkotyyppi) (s/maybe +verkkotyyppi+) ;; +verkkotyyppi+ skeemasta
    (s/optional-key :verkon-tarkoitus) (s/maybe +verkon-tarkoitus+)
    (s/optional-key :verkon-sijainti) (s/maybe +verkon-sijainti+)
    (s/optional-key :tekninen-toimenpide) (s/maybe +tekninen-toimenpide+) ;; +tekninen-toimenpide+ skeemasta
    (s/optional-key :poistettu) s/Bool}]

  ;; Työt ovat luokiteltu listaus tehdyistä töistä, valittavana on
  :tyot
  [{:tyyppi +paallystystyon-tyyppi+ ;; +paallystystyon-tyyppi+ skeemasta
    :tyo s/Str
    :tilattu-maara s/Num
    :toteutunut-maara s/Num
    :yksikko s/Str
    :yksikkohinta s/Num
    (s/optional-key :poistettu) s/Bool}] } )

(defn laske-muutokset-kokonaishintaan
  "Laskee jokaisesta työstä muutos tilattuun hintaan (POT-Excelistä 'Muutos hintaan') ja summataan yhteen."
  [tyot]
  (reduce + (mapv
              (fn [tyo]
                (* (- (:toteutunut-maara tyo) (:tilattu-maara tyo)) (:yksikkohinta tyo)))
              (filter #(not= true (:poistettu %)) tyot))))
  
