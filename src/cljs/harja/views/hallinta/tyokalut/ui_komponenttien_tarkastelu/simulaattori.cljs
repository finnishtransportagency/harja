(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.simulaattori
  "Rekisteröidyistä shared-komponenteista rakennettu rajattu sivusimulaattori."
  (:require [reagent.core :as r]
            [harja.ui.komponentit :as ui]
            [harja.ui.napit :as napit]
            [harja.ui.komponentit.simulaattori :as sopimus]))

(def ^:private esimerkkipuu
  [{:komponentti :lomake
    :lapset [{:komponentti :valintaryhma
              :asetukset {:otsikko "Urakkatyyppi"
                          :vaihtoehdot [{:arvo :hoito :nimi "Hoito"}
                                        {:arvo :paallyste :nimi "Päällyste"}
                                        {:arvo :vesivayla :nimi "Vesiväylä"}]}}
             {:komponentti :nappi
              :asetukset {:teksti "Tallenna"}}]}])

(defn- puun-rakenne
  [solmut]
  [:ul
   (for [[indeksi solmu] (map-indexed vector solmut)]
     ^{:key indeksi}
     [:li
      [:code (name (:komponentti solmu))]
      (when (seq (:lapset solmu))
        [puun-rakenne (:lapset solmu)])])])

(defn lahde
  []
  [:section.ui-simulaattori-lahde {:data-cy "ui-simulaattori-lahde"}
   [:header.ui-simulaattori-paneelin-otsake
    [:div
     [:p.ui-tarkastelu-ylaotsikko "Syöte"]
     [:h3 "Deklaratiivinen lähde"]]
    [:span.ui-simulaattori-merkki "Data"]]
   [:p.ui-simulaattori-kuvaus "Tämä rakennemalli annetaan validoinnille ja rendererille."]
   [:pre.ui-simulaattori-koodi [:code (pr-str esimerkkipuu)]]
   [:div.ui-simulaattori-puu
    [:h4 "Komponenttipuu"]
    [puun-rakenne esimerkkipuu]]])

(defn- solmun-sisalto [solmu tila]
  (case (:komponentti solmu)
    :lomake
    [ui/pino {:data-cy "ui-simulaattori-lomake"}
     (map-indexed (fn [indeksi lapsi]
                    ^{:key indeksi}
                    [solmun-sisalto lapsi tila])
                  (:lapset solmu))]

    :valintaryhma
    [ui/valintaryhma
     (merge {:id "ui-simulaattori-urakkatyyppi"
             :arvo @(:valinta tila)
             :vaihda-fn (fn [arvo] (reset! (:valinta tila) arvo))
             :ohje "Valinta tulee deklaratiivisen skenaarion vaihtoehdoista."}
            (:asetukset solmu))]

    :nappi
    [napit/nappi
     (get-in solmu [:asetukset :teksti])
     (fn [] (reset! (:tila tila) :onnistui))
     {:data-cy "ui-simulaattori-tallenna"}]

    [:p {:role "alert"} (str "Komponenttia ei voi renderöidä: " (:komponentti solmu))]))

(defn- validointi []
  (if-let [virheet (seq (sopimus/virheet esimerkkipuu))]
    [:section.ui-simulaattori-validointi {:data-cy "ui-simulaattori-validointi"}
     [:div {:data-cy "ui-simulaattori-virheet"}
      [:h3 "Validointi epäonnistui"]
      (for [virhe virheet]
        ^{:key virhe} [:p {:role "alert"} virhe])]]
    [:section.ui-simulaattori-validointi {:data-cy "ui-simulaattori-validointi"}
     [ui/tilaviesti {:tila :onnistui
                     :viesti "Lähde on kelvollinen ja voidaan renderöidä."
                     :data-cy "ui-simulaattori-validointi-tila"}]]))

(defn- puu [tila]
  (if (sopimus/kelvollinen? esimerkkipuu)
    [:section.ui-simulaattori-paneeli.ui-simulaattori-esikatselu {:data-cy "ui-simulaattori-esikatselu"}
     [:header.ui-simulaattori-paneelin-otsake
      [:div
       [:p.ui-tarkastelu-ylaotsikko "Tulos"]
       [:h3 "Renderöity esikatselu"]]
      [:span.ui-simulaattori-merkki.ui-simulaattori-merkki-tulos "UI"]]
     [:p.ui-simulaattori-kuvaus "Tämä alue on rendererin tuottama käyttöliittymä."]
     (for [solmu esimerkkipuu]
       ^{:key (:komponentti solmu)} [solmun-sisalto solmu tila])]
    [:section.ui-simulaattori-paneeli.ui-simulaattori-esikatselu {:data-cy "ui-simulaattori-esikatselu"}
     [:header.ui-simulaattori-paneelin-otsake
      [:h3 "Renderöity esikatselu"]]
     [ui/tilaviesti {:tila :virhe
                     :viesti "Esikatselua ei renderöidä, koska lähde ei ole kelvollinen."
                     :data-cy "ui-simulaattori-esikatselu-virhe"}]]))

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
