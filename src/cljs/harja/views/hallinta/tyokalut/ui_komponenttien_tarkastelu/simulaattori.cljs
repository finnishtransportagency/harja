(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.simulaattori
  "Rekisteröidyistä shared-komponenteista rakennettu rajattu sivusimulaattori."
  (:require [cljs.pprint :as pprint]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [reagent.core :as r]
            [harja.ui.perus :as ui]
            [harja.ui.perus.esimerkkisivut :as esimerkkisivut]
            [harja.ui.perus.simulaattori :as sopimus]
            [harja.ui.yleiset :as yleiset]
            [harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi :as rekisterointi]))

(def ^:private oletusesimerkki :lomake-perus)

(defn- puu-tekstina [puu]
  (str/trim (with-out-str (pprint/pprint puu))))

(defn- esimerkin-teksti [id]
  (puu-tekstina (:puu (esimerkkisivut/hae id))))

(defonce ^:private valittu-esimerkki (r/atom oletusesimerkki))
(defonce ^:private syote (r/atom (esimerkin-teksti oletusesimerkki)))

(defn- lue-syote
  "Lukee syötteen datana. Palauttaa {:puu ...} tai {:virhe teksti}."
  [teksti]
  (if (str/blank? teksti)
    {:virhe "Syöte on tyhjä. Lisää EDN-lista komponenttisolmuja tai palauta esimerkki."}
    (try
      (let [solmut (reader/read-string teksti)]
        (if (and (sequential? solmut) (every? map? solmut))
          {:puu solmut}
          {:virhe "Lähteen pitää olla lista komponenttisolmuja."}))
      (catch :default e
        {:virhe (str "EDN-lukuvirhe: " (.-message e))}))))

(def ^:private luettu-syote
  ;; Jaettu reaktio: syöte luetaan kerran, vaikka kolme paneelia käyttää tulosta.
  (r/track #(lue-syote @syote)))

(defn- puun-rakenne
  [solmut]
  [:ul
   (for [[indeksi solmu] (map-indexed vector solmut)]
     ^{:key indeksi}
     [:li
      [:code (some-> (:komponentti solmu) name)]
      (when (seq (:lapset solmu))
        [puun-rakenne (:lapset solmu)])])])

(defn- valitse-esimerkki! [id]
  (reset! valittu-esimerkki id)
  (when id
    (reset! syote (esimerkin-teksti id))))

(defn- esimerkkivalinta []
  (let [valittu (esimerkkisivut/hae @valittu-esimerkki)
        vaihtoehdot (into [nil] (map :id esimerkkisivut/esimerkkisivut))]
    [:div.ui-simulaattori-esimerkit
     [yleiset/pudotusvalikko
      "Esimerkkisivu"
      {:data-cy "ui-simulaattori-esimerkki"
       :elementin-id "ui-simulaattori-esimerkki"
       :valinta @valittu-esimerkki
       :valitse-fn valitse-esimerkki!
       :format-fn #(if %
                     (:nimi (esimerkkisivut/hae %))
                     "Oma syöte")
       :vayla-tyyli? true}
      vaihtoehdot]
     (when-let [kuvaus (some-> valittu :kuvaus)]
       [:p.ui-ohje kuvaus])]))

(defn- syotekentta []
  [:div.ui-simulaattori-syotteet
   [esimerkkivalinta]
   [:label {:for "ui-simulaattori-syote"} "EDN-lähde"]
   [:textarea.ui-simulaattori-syote
    {:id "ui-simulaattori-syote"
     :data-cy "ui-simulaattori-syote"
     :rows 12
     :spell-check false
     :value @syote
     :on-change #(reset! syote (.. % -target -value))}]
   [:button {:type "button"
             :data-cy "ui-simulaattori-palauta"
             :on-click #(valitse-esimerkki! (or @valittu-esimerkki oletusesimerkki))}
    "Palauta esimerkki"]])

(defn lahde
  []
  (let [{:keys [puu virhe]} @luettu-syote]
    [:section.ui-simulaattori-lahde {:data-cy "ui-simulaattori-lahde"}
     [:header.ui-simulaattori-paneelin-otsake
      [:div
       [:p.ui-tarkastelu-ylaotsikko "Syöte"]
       [:h3 "Deklaratiivinen lähde"]]
      [:span.ui-simulaattori-merkki "Data"]]
     [:p.ui-simulaattori-kuvaus "Tämä rakennemalli annetaan validoinnille ja rendererille."]
     [syotekentta]
     (when-not virhe
       [:div
        [:pre.ui-simulaattori-koodi [:code (pr-str puu)]]
        [:div.ui-simulaattori-puu
         [:h4 "Komponenttipuu"]
         [puun-rakenne puu]]])]))

(defn- solmun-sisalto [solmu tila]
  (if-let [renderoi (rekisterointi/renderoi (:komponentti solmu))]
    (renderoi solmu tila #(solmun-sisalto % tila))
    [:p {:role "alert"} (str "Komponenttia ei voi renderöidä: " (:komponentti solmu))]))

(defn- validointi []
  (let [{:keys [puu virhe]} @luettu-syote
        virheet (if virhe [virhe] (sopimus/virheet puu))]
    (if (seq virheet)
      [:section.ui-simulaattori-validointi {:data-cy "ui-simulaattori-validointi"}
       [:div {:data-cy "ui-simulaattori-virheet"}
        [:h3 "Validointi epäonnistui"]
        (for [virhe virheet]
          ^{:key virhe} [:p {:role "alert"} virhe])]]
      [:section.ui-simulaattori-validointi {:data-cy "ui-simulaattori-validointi"}
       [ui/tilaviesti {:tila :onnistui
                       :viesti "Lähde on kelvollinen ja voidaan renderöidä."
                       :data-cy "ui-simulaattori-validointi-tila"}]])))

(defn- puu [tila]
  (let [{:keys [puu virhe]} @luettu-syote]
    (if (and (nil? virhe) (sopimus/kelvollinen? puu))
      [:section.ui-simulaattori-paneeli.ui-simulaattori-esikatselu {:data-cy "ui-simulaattori-esikatselu"}
       [:header.ui-simulaattori-paneelin-otsake
        [:div
         [:p.ui-tarkastelu-ylaotsikko "Tulos"]
         [:h3 "Renderöity esikatselu"]]
        [:span.ui-simulaattori-merkki.ui-simulaattori-merkki-tulos "UI"]]
       [:p.ui-simulaattori-kuvaus "Tämä alue on rendererin tuottama käyttöliittymä."]
       (for [[indeksi solmu] (map-indexed vector puu)]
         ^{:key indeksi} [solmun-sisalto solmu tila])]
      [:section.ui-simulaattori-paneeli.ui-simulaattori-esikatselu {:data-cy "ui-simulaattori-esikatselu"}
       [:header.ui-simulaattori-paneelin-otsake
        [:h3 "Renderöity esikatselu"]]
       [ui/tilaviesti {:tila :virhe
                       :viesti "Esikatselua ei renderöidä, koska lähde ei ole kelvollinen."
                       :data-cy "ui-simulaattori-esikatselu-virhe"}]])))

(defn sivu []
  (r/with-let [valinta (r/atom :hoito)
               tila (r/atom :normaali)]
    (let [tila-atomi {:valinta valinta :tila tila}]
      [:main {:data-cy "ui-komponenttien-sivusimulaattori"}
       [:header
        [:p "Deklaratiivinen näkymä"]
        [:h2 "Sivusimulaattori"]
        [:p "Näe yhdellä silmäyksellä, miten rekisteröity rakenne muuttuu käyttöliittymäksi."]]
       [ui/pino {:data-cy "ui-simulaattori"}
        [:div.ui-simulaattori-tyopoyta {:data-cy "ui-simulaattori-tyopoyta"}
         [puu tila-atomi]
         [validointi]]
        (if (= :onnistui @tila)
          [ui/tilaviesti {:tila :onnistui
                          :viesti "Muutokset ovat päivittyneet esikatseluun."
                          :data-cy "ui-simulaattori-tila"}]
          [:p.ui-simulaattori-tila {:data-cy "ui-simulaattori-tila"}
           "Valitse arvo ja tarkista renderöity tulos."])]])))
