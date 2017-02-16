(ns harja-laadunseuranta.schemas
  (:require #?(:clj [schema.core :as s]
               :cljs [schema.core :as s :include-macros true])))

(defn validi-lumisuus? [arvo]
  (and (number? arvo) (<= 0 arvo 100)))

(defn validi-tasaisuus? [arvo]
  (and (number? arvo) (<= 0 arvo 100)))

(defn validi-kiinteys? [arvo]
  (and (number? arvo) (<= 1 arvo 5)))

(defn validi-polyavyys? [arvo]
  (and (number? arvo) (<= 1 arvo 5)))

(defn validi-kitka? [arvo]
  (and (number? arvo) (<= 0 arvo 1)))

(defn validi-sivukaltevuus? [arvo]
  (and (number? arvo) (<= 0 arvo 100)))

(defn validi-sijainti? [arvo]
  (and (= 2 (count arvo))
       (every? #(= 2 (count %)) arvo)))

(def Lumisuus (s/pred validi-lumisuus?))
(def Tasaisuus (s/pred validi-tasaisuus?))
(def Kitka (s/pred validi-kitka?))
(def Kiinteys (s/pred validi-kiinteys?))
(def Polyavyys (s/pred validi-polyavyys?))
(def Sivukaltevuus (s/pred validi-sivukaltevuus?))

(def Sijainti {:lon s/Num
               :lat s/Num
               (s/optional-key :accuracy) (s/maybe s/Num)})

(def TrOsoite {(s/optional-key :tie) (s/maybe s/Num)
               (s/optional-key :aosa) (s/maybe s/Num)
               (s/optional-key :aet) (s/maybe s/Num)
               (s/optional-key :losa) (s/maybe s/Num)
               (s/optional-key :let) (s/maybe s/Num)})

(def Kuva {:data s/Str
           :mime-type s/Str})

(def HavaintoKirjaus
  {:id s/Int
   (s/optional-key :ensimmainen-piste) s/Bool
   (s/optional-key :viimeinen-piste) s/Bool
   :tarkastusajo s/Int
   :aikaleima s/Int
   :sijainti Sijainti
   (s/optional-key :kayttajan-syottama-tr-osoite) (s/maybe TrOsoite)
   :mittaukset {(s/optional-key :lampotila) (s/maybe s/Num)
                (s/optional-key :lumisuus) (s/maybe Lumisuus)
                (s/optional-key :talvihoito-tasaisuus) (s/maybe Tasaisuus)
                (s/optional-key :soratie-tasaisuus) (s/maybe Tasaisuus)
                (s/optional-key :kitkamittaus) (s/maybe Kitka)
                (s/optional-key :kiinteys) (s/maybe Kiinteys)
                (s/optional-key :polyavyys) (s/maybe Polyavyys)
                (s/optional-key :sivukaltevuus) (s/maybe Sivukaltevuus)}
   :havainnot #{(s/enum
                  ;; Jatkuvat

                  :liukasta
                  :soratie
                  :tasauspuute
                  :lumista
                  :yli-tai-aliauraus

                  :vesakko-raivaamatta
                  :niittamatta
                  :reunapalletta
                  :reunataytto-puutteellinen

                  :kevatmuokkauspuute
                  :sorastuspuute
                  :kelirikkohavainnot

                  :saumavirhe
                  :lajittuma
                  :epatasaisuus
                  :vesilammikot
                  :epatasaisetreunat
                  :jyranjalkia
                  :sideainelaikkia
                  :vaarakorkeusasema
                  :pintaharva
                  :pintakuivatuspuute
                  :kaivojenkorkeusasema

                  :reikajono
                  :halkeamat
                  :purkaumat
                  :reunapainuma
                  :syvat-ajourat
                  :liikenneturvallisuutta-vaarantava-heitto
                  :ajomukavuutta-haittaava-epatasaisuus
                  :harjauspuute

                  ;; Pistekohtaiset

                  :liikennemerkki-likainen
                  :pl-alue-hoitamatta
                  :sillan-paallysteessa-vaurioita
                  :sillassa-kaidevaurioita
                  :sillassa-reunapalkkivaurioita
                  :liikennemerkki-luminen
                  :pysakilla-epatasainen-polanne
                  :aurausvalli
                  :sulamisvesihaittoja
                  :polanteessa-jyrkat-urat
                  :hiekoittamatta
                  :pysakki-auraamatta
                  :pysakki-hiekoittamatta
                  :pl-epatasainen-polanne
                  :pl-alue-auraamatta
                  :pl-alue-hiekoittamatta
                  :sohjoa
                  :irtolunta
                  :lumikielekkeita
                  :siltasaumoissa-puutteita
                  :siltavaurioita
                  :silta-puhdistamatta
                  :ojat-kivia-poistamatta
                  :ylijaamamassa-tasattu-huonosti
                  :oja-tukossa
                  :luiskavaurio
                  :istutukset-hoitamatta
                  :liikennetila-hoitamatta
                  :nakemaalue-raivaamatta
                  :liikennemerkki-vinossa
                  :reunapaalut-vinossa
                  :reunapaalut-likaisia
                  :pl-alue-puhdistettava
                  :pl-alue-korjattavaa
                  :viheralueet-hoitamatta
                  :rumpu-tukossa
                  :rumpu-liettynyt
                  :rumpu-rikki
                  :kaidevaurio
                  :kiveysvaurio
                  :yleishavainto
                  :maakivi
                  :liikennemerkki-vaurioitunut
                  :reunapaalut-vaurioitunut
                  :yksittainen-reika)}

   (s/optional-key :kuvaus) (s/maybe s/Str)
   (s/optional-key :liittyy-havaintoon) (s/maybe s/Int)
   (s/optional-key :laadunalitus) (s/maybe s/Bool)
   (s/optional-key :kuva) (s/maybe s/Int)})

(def Havaintokirjaukset
  {:kirjaukset [HavaintoKirjaus]})

(def TarkastuksenPaattaminen
  {:urakka s/Int
   :tarkastusajo {:id s/Int}})

(defn api-vastaus [ok-tyyppi]
  (s/conditional
    #(contains? % :error) {:error s/Str}
    :else {:ok ok-tyyppi}))
