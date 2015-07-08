(ns harja.domain.paallystys.pot
  "Ylläpidon päällystysurakoissa käytettävän POT-lomakkeen skeemat."
  (:require [schema.core :as s]
            [harja.domain.skeema :as skeema]))

(def +paallystetyypit+
  "Kaikki päällystetyypit POT-lomake Excelistä"
  [{:nimi "Betoni"                   :lyhenne "BET"   :koodi 1}
   {:nimi "Kivi"                     :lyhenne "KIVI"  :koodi 2}
   {:nimi "Avoin asfaltti"           :lyhenne "AA"    :koodi 11}  
   {:nimi "Asfalttibetoni"           :lyhenne "AB"    :koodi 12}
   {:nimi "Epäjatkuva asfaltti"      :lyhenne "EA"    :koodi 13}
   {:nimi "Kivimastiksiasfaltti"     :lyhenne "SMA"   :koodi 14}
   {:nimi "Kantavan kerroksen AB"    :lyhenne "ABK"   :koodi 15}
   {:nimi "Bit.sidottu kantava ker." :lyhenne "ABS"   :koodi 16}
   {:nimi "Valuasfaltti"             :lyhenne "VA"    :koodi 17}
   {:nimi "Pehmeä asfalttibetoni"    :lyhenne "PAB-B" :koodi 21}
   {:nimi "Pehmeä asfalttibetoni"    :lyhenne "PAB-V" :koodi 22}
   {:nimi "Pehmeä asfalttibetoni"    :lyhenne "PAB-O" :koodi 23}
   {:nimi "Sirotepintaus"            :lyhenne "SIP"   :koodi 24}
   {:nimi "Soratien pintaus"         :lyhenne "SOP"   :koodi 31}
   {:nimi "Sora"                     :lyhenne "SORA"  :koodi 41}])

(defn hae-paallyste-koodilla
  "Hakee päällysteen nimen koodilla"
  [koodi]
  (:nimi (first (filter #(= (:koodi %) koodi) +paallystetyypit+))))

(def +paallystetyyppi+ "Päällystetyypin valinta koodilla"
  (apply s/enum (map :koodi +paallystetyypit+)))

(def +tyomenetelmat+
  "Kaikki työmenetelmät POT-lomake Excelistä"
  [{:nimi "Paksuudeltaan vakio laatta"                 :lyhenne "LTA"  :koodi 12}
   {:nimi "Massapintaus"                               :lyhenne "MP"   :koodi 21}
   {:nimi "Kuumennuspintaus"                           :lyhenne "MPK"  :koodi 22}
   {:nimi "MP kuumalle, kuumajyrsitylle tas. pinnalle" :lyhenne "MPKJ" :koodi 23}
   {:nimi "REMIX-pintaus"                              :lyhenne "REM"  :koodi 31}
   {:nimi "2-kerroksinen remix-pintaus"                :lyhenne "REM+" :koodi 32}
   {:nimi "PAB-O/V:n remix-pintaus"                    :lyhenne "REMO" :koodi 33}
   {:nimi "ART-pintaus"                                :lyhenne "ART"  :koodi 34}
   {:nimi "Novachip-massapintaus"                      :lyhenne "NC"   :koodi 35}
   {:nimi "Karhinta"                                   :lyhenne "KAR"  :koodi 41}
   {:nimi "Hienojyrsintä"                              :lyhenne "HJYR" :koodi 51}
   {:nimi "Sirotepintaus"                              :lyhenne "SIP"  :koodi 61}
   {:nimi "Urapaikkaus"                                :lyhenne "UP"   :koodi 71}
   {:nimi "Uraremix"                                   :lyhenne "UREM" :koodi 72}])

(def +tyomenetelma+ "Työmenetelmän valinta koodilla"
  (apply s/enum (map :koodi +tyomenetelmat+)))

(def +alustamenetelmat+
  "Kaikki alustan käsittelymenetelmät POT-lomake Excelistä"
  [{:nimi "Massanvaihto"             :lyhenne "MV"   :koodi 1}
   {:nimi "Bitumiemusiostabilointi"  :lyhenne "BEST" :koodi 11}
   {:nimi "Vaahtobitumistabilointi"  :lyhenne "VBST" :koodi 12}
   {:nimi "Remix-stabilointi"        :lyhenne "REST" :koodi 13}
   {:nimi "Sementtistabilointi"      :lyhenne "SST"  :koodi 14}
   {:nimi "Masuunihiekkastabilointi" :lyhenne "MHST" :koodi 15}
   {:nimi "Komposiittistabilointi"   :lyhenne "KOST" :koodi 16}
   {:nimi "Kantavan kerroksen AB"    :lyhenne "ABK"  :koodi 21}
   {:nimi "Sidekerroksen AB"         :lyhenne "ABS"  :koodi 22}
   {:nimi "Murske"                   :lyhenne "MS"   :koodi 23}
   {:nimi "Kuumennustasaus"          :lyhenne "TASK" :koodi 31}
   {:nimi "Massatasaus"              :lyhenne "TAS"  :koodi 32}
   {:nimi "Tasausjyrsintä"           :lyhenne "TJYR" :koodi 41}
   {:nimi "Laatikkojyrsintä"         :lyhenne "LJYR" :koodi 42}
   {:nimi "Reunajyrsintä"            :lyhenne "RJYR" :koodi 43}
   ;;FIXME: puuttuu sekoitusjyrsintä SJYR
   ])
  
(def +alustamenetelma+ "Alustan käsittelymenetelmän valinta koodilla"
  (apply s/enum (map :koodi +alustamenetelmat+)))

(def +verkkotyypit+
  "Verkkotyypit POT-lomake Excelistä"
  [{:nimi "Teräsverkko"     :koodi 1}
   {:nimi "Lasikuituverkko" :koodi 2}
   {:nimi "Muu"             :koodi 9}])

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
  [{:nimi "Yksiajoratainen"                 :koodi 0}
   {:nimi "Kaksiajorataisen ensimmäinen"    :koodi 1}
   {:nimi "Kaksiajorataisen toinen ajorata" :koodi 2}])

(def +ajorata+
  "Ajoratavalinta koodilla"
  (apply s/enum (map :koodi +ajoradat+)))

(def +suunnat+
  "Suuntavalinnat"
  [{:nimi "Molemmat suunnat samassa"       :koodi 0}
   {:nimi "Tierekisterin suunta"           :koodi 1}
   {:nimi "Tierekisterin vastainen suunta" :koodi 2}])

(def +suunta+
  "Suuntavalinta koodilla"
  (apply s/enum (map :koodi +suunnat+)))

(def +kaistat+
  "Kaistavalinnat"
  [{:nimi "Pääkaista"     :koodi 1}
   {:nimi "Ohituskaista"  :koodi 2}
   {:nimi "Kolmas kaista" :koodi 3}])

(def +kaista+
  "Kaistavalinta koodilla"
  (apply s/enum (map :koodi +kaistat+)))

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
   :osoitteet [{:tie s/Int
                :aosa s/Int
                :aet s/Int
                :losa s/Int
                :let s/Int
                :ajorata +ajorata+
                :suunta +suunta+
                :kaista +kaista+
                ; Osoitteelle tehdyt toimenpiteet
                :paallystetyyppi +paallystetyyppi+
                :raekoko s/Int
                :massa s/Num ;; kg/m2
                :rc% s/Int
                :tyomenetelma +tyomenetelma+ ;; koodisto "työmenetelmä"
                :leveys s/Num  ;; metriä, esim. 4,2
                :massamaara s/Num ;; tonnia
                :pinta-ala s/Num ;; m2
                :edellinen-paallystetyyppi +paallystetyyppi+}]

   ;; N kpl kiviainesesiintymiä (ei liity osoitteiden järjestykseen)
   (s/optional-key :kiviaines) [{:esiintyma s/Str
                :km-arvo s/Str
                :muotoarvo s/Str
                :sideainetyyppi s/Str
                :pitoisuus s/Num
                :lisaaineet s/Str}]

   ;; Tieosoitteille tehtyjä toimia, mutta ei esitäytetä osoitteita, voi olla monta samalle
   ;; kohdallekin. Vaihtelee alustan laadun mukaan (esim. löytyy kiviä).
   ;; Välien tulee olla kohdeluettelon osoitteiden sisällä.
   (s/optional-key :alustatoimet) [{:tie s/Int :aosa s/Int :aet s/Int :losa s/Int :let s/Int
                   :kasittelymenetelma +alustamenetelma+ ;; +alustamenetelma+ skeemasta
                   :paksuus s/Num ;; cm
                   :verkkotyyppi +verkkotyyppi+ ;; +verkkotyyppi+ skeemasta
                   :tekninen-toimenpide +tekninen-toimenpide+ ;; +tekninen-toimenpide+ skeemasta
                  }]

   ;; Työt ovat luokiteltu listaus tehdyistä töistä, valittavana on 
   ;; toimenpidekoodistosta 4. tason tehtäviä (ylläpito, tie / päällyste / ...) 
   (s/optional-key :tyot) [{:tyyppi +paallystystyon-tyyppi+ ;; +paallystystyon-tyyppi+ skeemasta
           :toimenpidekoodi s/Int ;; 4. tason toimenpidekoodi
           :tilattu-maara s/Num
           :toteutunut-maara s/Num
           :yksikko s/Str
           :yksikkohinta s/Num}]
  })
  
