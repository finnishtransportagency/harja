(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys
  "Tarkastelusivun oma runko. Ei shared-komponentteja: tänne kuuluu vain sivun asettelu,
  navigaatio, kortti ja sopimusruutu.

  Ankkurit johdetaan rekisteristä (`rekisteri/ankkuri`), jotta kortin, navigaation ja
  sopimusruudun ankkurit eivät voi erota toisistaan."
  (:require [clojure.string :as str]
            [harja.ui.perus :as ui]
            [harja.ui.perus.rekisteri :as rekisteri]))

(defn ankkuri
  "Komponentin ankkuri rekisteristä, valinnaisilla päätteillä sisäisiä elementtejä varten."
  [komponentti & osat]
  (apply str (rekisteri/ankkuri komponentti) osat))

(defn bootstrap-tila-merkki [{:keys [bootstrap-tila]}]
  (let [tila (if (contains? rekisteri/bootstrap-tilat bootstrap-tila)
               bootstrap-tila
               :maarittelematta)]
    [:span.ui-tarkastelu-tila
     {:class (str "ui-tarkastelu-tila-" (name tila))
      :data-cy "ui-komponenttien-tarkastelu-bootstrap-tila"}
     (get-in rekisteri/bootstrap-tilat [tila :teksti])]))

(defn legacy-paatos-merkki
  ([komponentti]
   (legacy-paatos-merkki komponentti false))
  ([komponentti lyhyt?]
   (let [paatos (rekisteri/legacy-paatos komponentti)
         teksti-avain (if lyhyt? :lyhyt-teksti :teksti)]
     [:span.ui-tarkastelu-legacy-paatos
      {:class (str "ui-tarkastelu-legacy-paatos-" (name paatos))
       :data-cy (ankkuri komponentti
                  (if lyhyt?
                    "-legacy-paatos-valinta"
                    "-legacy-paatos"))}
      (get-in rekisteri/legacy-paatokset [paatos teksti-avain])])))

(defn kehys
  [{:keys [otsikko kuvaus simulaattori? koko-levea?]} navigaatio sisalto sopimus]
  [:div {:class (str "ui-tarkastelu"
                     (when simulaattori? " ui-tarkastelu-simulaattori")
                     (when koko-levea? " ui-tarkastelu-koko-levea"))
         :data-cy "ui-komponenttien-tarkastelu-sivu"}
   [:header.ui-tarkastelu-otsake
    [:p.ui-tarkastelu-ylaotsikko "Harja / UI"]
    [:h1 otsikko]
    [:p kuvaus]]
   [:div.ui-tarkastelu-tyopoyta
    navigaatio
    sisalto
    sopimus]])

(defn navigaatio
  [{:keys [otsikko ryhmat valittu valitse-fn nayta-elinkaaripaatos? data-cy]
    :or {nayta-elinkaaripaatos? true
         data-cy "ui-komponenttien-tarkastelu-komponenttiluettelo"}}]
  [:nav.ui-tarkastelu-navigaatio
   {:aria-label otsikko
    :data-cy data-cy}
   (for [{:keys [id nimi komponentit]} ryhmat]
     ^{:key id}
     [:section
      [:h2.ui-tarkastelu-ryhma nimi]
      (for [{komponentin-id :id :keys [nimi] :as komponentti} komponentit]
        ^{:key komponentin-id}
        [:button
         {:type "button"
          :class (when (= komponentin-id valittu) "aktiivinen")
          :aria-current (when (= komponentin-id valittu) "true")
          :data-cy (ankkuri komponentti "-valinta")
          :on-click #(valitse-fn komponentin-id)}
         [:span nimi]
         (when nayta-elinkaaripaatos?
           [legacy-paatos-merkki komponentti true])])])])

(defn sisalto
  "Valitun komponentin otsikko ja esikatselu."
  [{:keys [nimi kuvaus]} ryhman-nimi esikatselu]
  [:main.ui-tarkastelu-sisalto
   {:data-cy "ui-komponenttien-tarkastelu-valittu-komponentti"}
   [:header.ui-tarkastelu-sisallon-otsake
    [:p.ui-tarkastelu-ylaotsikko ryhman-nimi]
    [:h2 nimi]
    [:p kuvaus]]
   esikatselu])

(defn kortti
  "Esikatselun kortti. Juuriankkuri tulee rekisteristä."
  [komponentti & lapset]
  (let [ankkuri (ankkuri komponentti)]
    [ui/kortti {:id (str ankkuri "-esikatselu")
                :data-cy ankkuri
                :otsikko "Esikatselu"
                :otsikkotaso 3
                :luokka "ui-tarkastelu-kortti"}
     (into [ui/pino {}] lapset)]))

(defn asetukset-taulukko
  [asetukset]
  [:table {:data-cy "ui-komponenttien-tarkastelu-asetukset"}
   [:thead
    [:tr
     [:th "Asetus"] [:th "Tyyppi"] [:th "Pakollinen"] [:th "Kuvaus"]]]
   [:tbody
    (for [[avain {:keys [tyyppi pakollinen? kuvaus]}] (sort-by key asetukset)]
      ^{:key avain}
      [:tr
       [:td [:code (str avain)]]
       [:td (str tyyppi)]
       [:td (if pakollinen? "Kyllä" "Ei")]
       [:td kuvaus]])]])

(defn sopimus
  [{:keys [asetukset] :as komponentti} & lapset]
  (into
    [:aside.ui-tarkastelu-sopimus
     {:data-cy "ui-komponenttien-tarkastelu-tiedot"}
     [:h2.ui-tarkastelu-ylaotsikko "Komponentin sopimus"]
     [:dl
      [:dt "Public API"]
      [:dd [:code (rekisteri/api-teksti komponentti)]]
      [:dt "Asetukset"]
      [:dd [asetukset-taulukko asetukset]]
      [:dt "Suositus"]
      [:dd [:span {:data-cy "ui-komponenttien-tarkastelu-suositus"} (some-> (:suositus komponentti) name)]]
      [:dt "Elinkaaripäätös"]
      [:dd [legacy-paatos-merkki komponentti]]
      [:dt "Korvaa"]
      [:dd (if (seq (:korvaa komponentti))
             (str/join ", " (:korvaa komponentti))
             "Ei korvaa mitään")]
      [:dt "Bootstrap-tila"]
      [:dd [bootstrap-tila-merkki komponentti]]
      [:dt "DOM-ankkuri"]
      [:dd [:code (str "data-cy=" (rekisteri/ankkuri komponentti))]]]]
    lapset))
