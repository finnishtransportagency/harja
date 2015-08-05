(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [virheen-ohje]]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo atomina]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]))

(defrecord Ryhma [otsikko skeemat])

(defn ryhma [otsikko & skeemat]
  (->Ryhma otsikko skeemat))

(defn ryhma? [x]
  (instance? Ryhma x))

(defmulti kentan-otsikko (fn [luokka kentan-nimi teksti] luokka))

(defmethod kentan-otsikko :horizontal [_ kentan-nimi teksti]
  [:label.col-sm-2.control-label {:for kentan-nimi}
   teksti])

(defmethod kentan-otsikko :default [_ kentan-nimi teksti]
  [:label {:for kentan-nimi} teksti])

(defmulti kentan-komponentti (fn [luokka skeema komponentti] luokka))

(defmethod kentan-komponentti :horizontal [_ skeema komponentti]
  [:div {:class (str "col-sm-" (or (:leveys-col skeema) 10))}
   komponentti])

(defmethod kentan-komponentti :default [_ skeema komponentti]
  komponentti)

(defmulti lomake-footer (fn [luokka footer] luokka))

(defmethod lomake-footer :horizontal [_ footer]
  [:div.form-group.lomake-footer
   [:div.col-sm-offset-2.col-sm-10
    footer]])

(defmethod lomake-footer :default [_ footer]
  footer)

(defmulti tyhja-otsikko (fn [tyyppi] tyyppi))

(defmethod tyhja-otsikko :horizontal [_]
  [:label.col-sm-2])

(defmethod tyhja-otsikko :default [_]
  [:span])

(defmulti kentan-yksikko (fn [luokka _] luokka))

(defmethod kentan-yksikko :default [_ _]
  nil)

(defmethod kentan-yksikko :horizontal [_ {yks :yksikko}]
  (when yks [:div.inline-block.lomake-yksikko yks]))

(defmulti kentan-vihje (fn [luokka _] luokka))

(defmethod kentan-vihje :default [_ _]
  nil)

(defmethod kentan-vihje :horizontal [_ {vihje :vihje}]
  (when vihje [:div.row
               [:div.col-sm-2]
                [:div {:class
                       (str "inline-block lomake-vihje col-sm-10")}
                 [:div.vihjeen-sisalto
                  (harja.ui.ikonit/info-sign)
                  (str " " vihje)]]]))

(def +ei-otsikkoa+ #{:boolean})


(defn lomake
  "Geneerinen lomakekomponentti, joka käyttää samaa kenttien määrittelymuotoa kuin grid.
  Ottaa kolme parametria: optiot, skeeman (vektori kenttiä) sekä datan (mäppi).

  Kenttiä voi poistaa käytöstä ehdollisesti, kaikki nil kentät jätetään näyttämättä.
  Kenttä voi olla myös ryhmä (ks. ryhma funktio), jolla on otsikko ja kentät.
  Ryhmistä muodostetaan fieldset lomakkeelle.

  Optioissa voi olla seuraavat avaimet:

  :muokkaa!       callback, jolla kaikki muutokset tehdään, ottaa sisään uudet tiedot
                  ja tekee sille jotain (oletettavasti swap! tai reset! atomille,
                  joka sisältää lomakkeen tiedot

  :luokka         lomakkeen tyyli: tuetut ovat :inline, :horizontal ja :default

  :footer         Komponentti, joka asetetaan lomakkeen footer sijaintiin, yleensä
                  submit nappi tms.

  :footer-fn      vaihtoehto :footer'lle, jolle annetaan footerin muodostava funktio
                  funktiolle annetaan virheet ja varoitukset parametrina 

  :virheet        atomi, joka sisältää mäpin kentän nimestä virheisiin, jos tätä ei
                  anneta, lomake luo sisäisesti uuden atomin.
  :varoitukset    atomi, joka sisältää mäpin kentän nimestä varoituksiin, jos tätä ei
                  anneta, lomake luo sisäisesti uuden atomin.
  :voi-muokata?   voiko lomaketta muokata, oletuksena true
  "

  [{:keys [muokkaa! luokka footer virheet varoitukset voi-muokata?] :as opts} skeema data]
  (let [luokka (or luokka :default)
        voi-muokata? (if (some? voi-muokata?)
                       voi-muokata?
                       true)
        ;; Kaikki kentät, joita käyttäjä on muokannut
        muokatut (atom #{})
        nykyinen-fokus (atom nil)
        aseta-fokus! #(reset! nykyinen-fokus %)
        paivita-ulkoinen-validointitila! (fn [virheet-atom virheet varoitukset-atom varoitukset]
                                           ;; Päivitetään ulkoinen validointitila vain, jos atomit on
                                           ;; annettu ja uusi virhelista on muuttunut. Tämä estää
                                           ;; ulkoisesti annetun atomin joka luetaan samassa komponentista
                                           ;; aiheuttamast ikuista render-looppia.
                                           (when (and virheet-atom
                                                      (not= virheet @virheet-atom))
                                             (reset! virheet-atom virheet))
                                           (when (and varoitukset-atom
                                                      (not= varoitukset @varoitukset-atom))
                                             (reset! varoitukset-atom varoitukset)))]
    
    (paivita-ulkoinen-validointitila! virheet (into {}
                                                    (validointi/validoi-rivi nil data skeema :validoi))
                                      varoitukset (into {}
                                                        (validointi/validoi-rivi nil data skeema :varoita)))

    (fn [{:keys [muokkaa! luokka footer footer-fn virheet varoitukset] :as opts} skeema data]
      (let [kaikki-skeemat (keep identity (mapcat #(if (ryhma? %) (:skeemat %) [%]) skeema))
            kaikki-virheet (validointi/validoi-rivi nil data kaikki-skeemat :validoi)
            kaikki-varoitukset (validointi/validoi-rivi nil data kaikki-skeemat :varoita)
            _ (paivita-ulkoinen-validointitila! virheet kaikki-virheet varoitukset kaikki-varoitukset)
            kentta (fn [{:keys [muokattava? fmt hae nimi pakollinen? validoi-heti?] :as kentta}]
                     (assert (not (nil? nimi)) (str "Virheellinen kentän määrittely, :nimi arvo nil. Otsikko: " (:otsikko kentta)))
                     (let [kentan-virheet (get kaikki-virheet nimi)
                           kentan-varoitukset (get kaikki-varoitukset nimi)
                           kentta (assoc kentta :lomake? true)
                           arvo (atomina kentta data (fn [uudet-tiedot]
                                                       (swap! muokatut conj nimi)
                                                       (muokkaa! uudet-tiedot)))
                           kentan-tunniste nimi]
                        ^{:key (:nimi kentta)}
                        [:div.form-group {:class (when pakollinen? "required")}
                         [:div.row
                          (if (+ei-otsikkoa+ (:tyyppi kentta))
                            [tyhja-otsikko luokka]
                            [kentan-otsikko luokka (name nimi) (:otsikko kentta)])
                          [kentan-komponentti luokka kentta
                           (if-let [komponentti (:komponentti kentta)]
                             komponentti
                             (if (and voi-muokata?
                                      (or (nil? muokattava?)
                                          (muokattava? data)))
                               ;; Muokattava tieto, tehdään sille kenttä
                               [:span {:class (str (when-not (empty? kentan-virheet)
                                                     "sisaltaa-virheen")
                                                   (when-not (empty? kentan-varoitukset)
                                                     "sisaltaa-varoituksen"))}
                                [tee-kentta (assoc kentta
                                                   :focus (= @nykyinen-fokus kentan-tunniste)
                                                   :on-focus #(aseta-fokus! kentan-tunniste)) arvo]
                                (if (and (not (empty? kentan-virheet))
                                         (or validoi-heti?
                                             (@muokatut nimi)))
                                  (virheen-ohje kentan-virheet :virhe)
                                  (if (and (not (empty? kentan-varoitukset))
                                           (@muokatut nimi))
                                    (virheen-ohje kentan-varoitukset :varoitus)))]

                               ;; Ei muokattava, näytetään
                               [:div.form-control-static
                                (if fmt
                                  (fmt ((or hae #(get % nimi)) data))
                                  (nayta-arvo kentta arvo))]))]
                          [kentan-yksikko luokka kentta]]
                         [kentan-vihje luokka kentta]]))]
        [:form.lomake {:class (case luokka
                                :inline "form-inline"
                                :horizontal "form-horizontal"
                                :default "")}
         
         (doall
          (for [skeema (keep identity skeema)]
            (if-let [ryhma (and (ryhma? skeema) skeema)]
              ^{:key (:otsikko ryhma)}
              [:fieldset
               [:legend (:otsikko ryhma)]
               (doall (map kentta (keep identity (:skeemat ryhma))))]

              (kentta skeema))))

         (if footer-fn
           [lomake-footer luokka (footer-fn kaikki-virheet kaikki-varoitukset)]
           (if footer
             [lomake-footer luokka footer]))]))))

