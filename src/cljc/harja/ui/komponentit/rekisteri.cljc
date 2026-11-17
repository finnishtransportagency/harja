(ns harja.ui.komponentit.rekisteri
  "Komponenttien tarkastelusivun rekisteri: komponenttien kuvaus datana.

  Rekisteri on tarkastelusivun kortin, navigaation ja sopimusruudun yhteinen lähde. Uusi
  komponentti lisätään tänne ensin, ja sen esikatselu liitetään samalla `:id`:llä
  tarkastelusivun esikatselut-kartassa.

  Rekisterissä ei ole renderöintiä, jotta sen eheys voidaan testata JVM:llä.")

(def ankkuri-etuliite "ui-komponenttien-tarkastelu-")

(def ryhmat
  "Navigaation ryhmät esitysjärjestyksessä."
  [{:id :alasvedot :nimi "Alasvedot"}
   {:id :toiminnot :nimi "Toiminnot"}
   {:id :lomakkeet :nimi "Lomakkeet"}])

(def luonteet
  "Mikä komponentti on: yksittäinen shared-komponentti vai niistä koottu kokonaisuus."
  #{:shared-komponentti :shared-rakenne})

(def bootstrap-tilat
  "Komponentin riippuvuus Bootstrapista. Teksti näytetään tarkastelusivulla."
  {:ei-riippuvuutta {:teksti "Ei Bootstrap-riippuvuutta"}
   :osittain-riippuvainen {:teksti "Osittain Bootstrap-riippuvainen"}
   :riippuvainen {:teksti "Bootstrap-riippuvainen"}
   :maarittelematta {:teksti "Bootstrap-tila määrittelemättä"}})

(def komponentit
  [{:id :livi-pudotusvalikko
    :nimi "Livi-pudotusvalikko"
    :ryhma :alasvedot
    :luonne :shared-komponentti
    :bootstrap-tila :riippuvainen
    :kuvaus "Nykyinen tuotannossa käytetty dropdown-sopimus."
    :api "harja.ui.yleiset/pudotusvalikko: otsikko + asetuskartta + vaihtoehtovektori"}
   {:id :nappi
    :nimi "Nappi"
    :ryhma :toiminnot
    :luonne :shared-komponentti
    :bootstrap-tila :maarittelematta
    :kuvaus "Yhteinen toimintopainike, jonka käyttöpaikka välittää tekstin ja toiminnon."
    :api "harja.ui.napit/nappi: teksti + callback + optiot (:data-cy, :disabled, :luokka)"}
   {:id :suodatin
    :nimi "Suodatin"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Suodattimen label ja kenttä. Käytössä tarjoushintojen näkymässä."
    :api "harja.ui.komponentit/suodatin, suodatinrivi: {:otsikko :id :data-cy} + kenttä (hiccup tai fn)"}
   {:id :valintaryhma
    :nimi "Valintaryhmä"
    :ryhma :lomakkeet
    :luonne :shared-komponentti
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Saavutettava natiivin select-kentän ympärille rakennettu valintaryhmä."
    :api "harja.ui.komponentit/valintaryhma: {:otsikko :id :vaihtoehdot :arvo :vaihda-fn :tyhja-valinta :ohje :virhe :pakollinen? :disabled?}"}
   {:id :lomake
    :nimi "Lomake"
    :ryhma :lomakkeet
    :luonne :shared-rakenne
    :bootstrap-tila :ei-riippuvuutta
    :kuvaus "Pieni lomakekokonaisuus, jossa näkymä omistaa arvon ja tallennuksen ehdot."
    :api "harja.ui.komponentit: pino + kenttaryhma + valintaryhma + toimintoryhma + tilaviesti"}])

(defn ankkuri
  "Komponentin vakaa juuriankkuri (data-cy). Johdetaan id:stä, ei tallenneta erikseen."
  [{:keys [id]}]
  (str ankkuri-etuliite (name id)))

(defn hae
  "Komponentti id:llä tai nil."
  ([id] (hae komponentit id))
  ([komponentit id]
   (first (filter #(= id (:id %)) komponentit))))

(defn ryhmittele
  "Palauttaa vain ne ryhmät, joissa on komponentteja: [{:id :nimi :komponentit [...]}]."
  ([] (ryhmittele ryhmat komponentit))
  ([ryhmat komponentit]
   (into []
     (keep (fn [{:keys [id] :as ryhma}]
             (when-let [ryhman-komponentit (seq (filter #(= id (:ryhma %)) komponentit))]
               (assoc ryhma :komponentit (vec ryhman-komponentit)))))
     ryhmat)))

(defn virheet
  "Palauttaa rekisterin eheysongelmat tekstinä. Tyhjä lista tarkoittaa, että rekisteri on kunnossa."
  ([] (virheet ryhmat komponentit))
  ([ryhmat komponentit]
   (let [ryhma-idt (set (map :id ryhmat))
         tuplat (fn [avain]
                  (->> komponentit (map avain) frequencies
                    (keep (fn [[arvo maara]] (when (> maara 1) arvo)))))]
     (vec
       (concat
         (for [arvo (tuplat :id)] (str "Tuplaid: " arvo))
         (for [{:keys [id ryhma luonne bootstrap-tila nimi kuvaus api]} komponentit
               virhe [(when-not (contains? ryhma-idt ryhma) (str id ": tuntematon ryhmä " ryhma))
                      (when-not (contains? luonteet luonne) (str id ": tuntematon luonne " luonne))
                      (when-not (contains? bootstrap-tilat bootstrap-tila)
                        (str id ": tuntematon bootstrap-tila " bootstrap-tila))
                      (when-not (every? seq [nimi kuvaus api]) (str id ": nimi, kuvaus tai api puuttuu"))]
               :when virhe]
           virhe))))))
