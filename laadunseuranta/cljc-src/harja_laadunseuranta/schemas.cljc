(ns harja-laadunseuranta.schemas
  (:require #?(:clj  [schema.core :as s]
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
               :lat s/Num})

(def Kuva {:data s/Str
           :mime-type s/Str})

(def HavaintoKirjaus
  {:id s/Int
   (s/optional-key :ensimmainen-piste) s/Bool
   (s/optional-key :viimeinen-piste) s/Bool
   :tarkastusajo s/Int
   :aikaleima s/Int
   :sijainti Sijainti
   :mittaukset {(s/optional-key :lampotila) (s/maybe s/Num)
                (s/optional-key :lumisuus) (s/maybe Lumisuus)
                (s/optional-key :tasaisuus) (s/maybe Tasaisuus)
                (s/optional-key :kitkamittaus) (s/maybe Kitka)
                (s/optional-key :kiinteys) (s/maybe Kiinteys)
                (s/optional-key :polyavyys) (s/maybe Polyavyys)
                (s/optional-key :sivukaltevuus) (s/maybe Sivukaltevuus)}
   :havainnot [(s/enum :liukasta :soratie :tasauspuute :lumista :liikennemerkki-luminen :pysakilla-epatasainen-polanne
                       :aurausvalli :sulamisvesihaittoja :polanteessa-jyrkat-urat :hiekoittamatta
                       :pysakki-auraamatta :pysakki-hiekoittamatta :pl-epatasainen-polanne :pl-alue-auraamatta
                       :pl-alue-hiekoittamatta :sohjoa :irtolunta :lumikielekkeita
                       :siltasaumoissa-puutteita :siltavaurioita :silta-puhdistamatta
                       :ojat-kivia-poistamatta :ylijaamamassa-tasattu-huonosti :oja-tukossa
                       :luiskavaurio :reunataytto-puutteellinen :reunapalletta
                       :istutukset-hoitamatta :liikennetila-hoitamatta
                       :nakemaalue-raivaamatta :niittamatta :vesakko-raivaamatta
                       :liikennemerkki-vinossa
                       :reunapaalut-vinossa :reunapaalut-likaisia
                       :pl-alue-puhdistettava :pl-alue-korjattavaa :viheralueet-hoitamatta
                       :rumpu-tukossa :rumpu-liettynyt :rumpu-rikki
                       :kaidevaurio :kiveysvaurio
                       :yleishavainto
                       :saumavirhe :lajittuma :epatasaisuus :halkeamat :vesilammikot :epatasaisetreunat
                       :jyranjalkia :sideainelaikkia :vaarakorkeusasema :pintaharva :pintakuivatuspuute :kaivojenkorkeusasema)]

   (s/optional-key :kuvaus) (s/maybe s/Str)
   (s/optional-key :laadunalitus) (s/maybe s/Bool)
   (s/optional-key :kuva) (s/maybe s/Int)})

(def Havaintokirjaukset
  {:kirjaukset [HavaintoKirjaus]})

(def TROsoite
  {:tie s/Int
   :aosa s/Int
   :aet s/Int})

(def TarkastuksenPaattaminen
  {:urakka (s/maybe s/Int)
   :tarkastusajo {:id s/Int}})

(defn api-vastaus [ok-tyyppi]
  (s/conditional
   #(contains? % :error) {:error s/Str}
   :else {:ok ok-tyyppi}))
