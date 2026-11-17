(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys
  "Tarkastelusivun oma runko. Ei shared-komponentteja: tänne kuuluu vain sivun asettelu,
  navigaatio, kortti ja sopimusruutu.

  Ankkurit johdetaan rekisteristä (`rekisteri/ankkuri`), jotta kortin, navigaation ja
  sopimusruudun ankkurit eivät voi erota toisistaan."
  (:require [harja.ui.komponentit.rekisteri :as rekisteri]))

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

(defn kehys
  [{:keys [otsikko kuvaus]} navigaatio sisalto sopimus]
  [:div.ui-tarkastelu
   {:data-cy "ui-komponenttien-tarkastelu-sivu"}
   [:header.ui-tarkastelu-otsake
    [:p.ui-tarkastelu-ylaotsikko "Harja / UI"]
    [:h1 otsikko]
    [:p kuvaus]]
   [:div.ui-tarkastelu-tyopoyta
    navigaatio
    sisalto
    sopimus]])

(defn navigaatio
  [{:keys [otsikko ryhmat valittu valitse-fn]}]
  [:nav.ui-tarkastelu-navigaatio
   {:aria-label otsikko
    :data-cy "ui-komponenttien-tarkastelu-komponenttiluettelo"}
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
         nimi])])])

(defn sisalto
  "Valitun komponentin otsikko ja esikatselu."
  [{:keys [nimi kuvaus] :as komponentti} ryhman-nimi esikatselu]
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
  [:section.ui-tarkastelu-kortti
   {:data-cy (ankkuri komponentti)}
   [:h3 "Esikatselu"]
   (into [:div.ui-tarkastelu-kortin-sisalto] lapset)])

(defn sopimus
  [{:keys [api] :as komponentti}]
  [:aside.ui-tarkastelu-sopimus
   {:data-cy "ui-komponenttien-tarkastelu-tiedot"}
   [:h2.ui-tarkastelu-ylaotsikko "Komponentin sopimus"]
   [:dl
    [:dt "Public API"]
    [:dd api]
    [:dt "Bootstrap-tila"]
    [:dd [bootstrap-tila-merkki komponentti]]
    [:dt "DOM-ankkuri"]
    [:dd [:code (str "data-cy=" (rekisteri/ankkuri komponentti))]]]])

(defn esikatselua-ei-ole [komponentti]
  [kortti komponentti
   [:p "Komponentti on rekisterissä, mutta sille ei ole esikatselua. Lisää esikatselu tämän id:n kohdalle."]])
