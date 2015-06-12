(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [virheen-ohje]]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo atomina]]
            [harja.loki :refer [log logt tarkkaile!]]))

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

  :virheet        atomi, joka sisältää mäpin kentän nimestä virheisiin, jos tätä ei
                  anneta, lomake luo sisäisesti uuden atomin.
"                   
 
  [{:keys [muokkaa! luokka footer virheet] :as opts} skeema data]
  (let [luokka (or luokka :default)
        virheet-atom (or virheet (atom {}))  ;; validointivirheet: (:id rivi) => [virheet]
        koskemattomat (atom (into #{} (map :nimi skeema)))]

    ;; Validoidaan kaikki kentät heti lomakkeen luontivaiheessa,
    ;; koskemattomien kenttien virheitä ei kuitenkaan näytetä.
    (reset! virheet-atom
            (into {}
                  (validointi/validoi-rivi nil data skeema)))
    
    (fn [{:keys [muokkaa! luokka footer virheet] :as opts} skeema data]
      (let [virheet (or virheet virheet-atom)]
        [:form.lomake {:class (case luokka
                                :inline "form-inline"
                                :horizontal "form-horizontal"
                                :default "")}
         (let [kentta (fn [{:keys [muokattava? fmt hae nimi] :as kentta}]
                        (assert (not (nil? nimi)) (str "Virheellinen kentän määrittely, :nimi arvo nil. Otsikko: " (:otsikko kentta)))
                        (let [kentan-virheet (get @virheet nimi)
                              kentta (assoc kentta :lomake? true)
                              arvo (atomina kentta data (fn [uudet-tiedot]
                                                          (reset! virheet
                                                                  (validointi/validoi-rivi nil uudet-tiedot skeema))
                                                          (swap! koskemattomat disj nimi)
                                                          (muokkaa! uudet-tiedot)))]
                          ^{:key (:nimi kentta)}
                          [:div.form-group
                           (if (+ei-otsikkoa+ (:tyyppi kentta))
                             [tyhja-otsikko luokka]
                             [kentan-otsikko luokka (name nimi) (:otsikko kentta)])
                           [kentan-komponentti luokka kentta
                            (if-let [komponentti (:komponentti kentta)]
                              komponentti
                              (if (or (nil? muokattava?)
                                      (muokattava? data))
                                ;; Muokattava tieto, tehdään sille kenttä
                                [:span {:class (str (when-not (empty? kentan-virheet)
                                                      "has-error"))}
                                 [tee-kentta kentta arvo]
                                 (when (and (not (empty? kentan-virheet))
                                            (not (@koskemattomat nimi)))
                                   (virheen-ohje kentan-virheet))]
                              
                                ;; Ei muokattava, näytetään
                                [:div.form-control-static
                                 (if fmt
                                   (fmt ((or hae #(get % nimi)) data))
                                   (nayta-arvo kentta arvo))]))]]))]
           (doall
            (for [skeema (keep identity skeema)]
              (if-let [ryhma (and (ryhma? skeema) skeema)]
                ^{:key (:otsikko ryhma)}
                [:fieldset
                 [:legend (:otsikko ryhma)]
                 (doall (map kentta (keep identity (:skeemat ryhma))))]
              
                (kentta skeema)))))
       
         (when footer
           [lomake-footer luokka footer])]))))

