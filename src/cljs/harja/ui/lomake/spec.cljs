(ns harja.ui.lomake.spec
  "Lomakkeen spec validointi.

  Laajentaa lomakkeen normaalia skeemapohjaista validointia
  clojure.spec validoinnilla. Päättelee puuttuvat pakolliset
  kentät sekä tietyt virheet explain-data problems listan perusteella.

  Ulkoinen rajapinta on validoi-spec funktio.

  Multimetodi pred-virhe yrittää tehdä selkokielisen virheviestin spec
  predikaatin perusteella. Koska predikaatit voivat olla mielivaltaisia
  funktioita, ei se osaa kaikkea. Tunnistaminen tapahtuu predikaattifunktion
  perusteella. Lisää uusi metodi, jos tarvit virheen tietylle predikaatille."
  (:require [clojure.spec :as s]
            [clojure.string :as str]))


(defn- puuttuvat-spec-kentat
  "Palauttaa spec problems listasta pakolliset puuttuvat kentät.
  Pakollinen puuttuva kenttä päätellään predikaatista (contains? % :kentan/keyword)."
  [problems]
  (into #{}
        (keep #(let [{:keys [path pred]} %]
                 (when (and (= [] path)
                            (list? pred)
                            (= (first pred) 'contains?))
                   (nth pred 2))))
        problems))


(defmulti pred-virhe
  "Palauttaa lomakkeessa näytettävän virheen spec validointivirheen predikaatin perusteella."
  (fn [{pred :pred}]
    (if (list? pred)
      (first pred)
      pred)))

(defmethod pred-virhe 'int? [{arvo :val}]
  "Syötä kokonaisluku")

(defmethod pred-virhe 'int-in-range? [{[_ min-inclusive max-exclusive] :pred
                                         arvo :val}]
  (cond
    (< arvo min-inclusive)
    (str "Liian pieni. Sallittu arvo välillä " min-inclusive " \u2013 "
         (dec max-exclusive))

    (>= arvo max-exclusive)
    (str "Liian suuri. Sallittu arvo välillä " min-inclusive " \u2013 "
         (dec max-exclusive))

    :default
    nil))

(defmethod pred-virhe :default [_] nil)

(defn- virheet [problems]
  (reduce
   (fn [virheet {:keys [path val pred] :as problem}]
     (if-let [puuttuva (or (and (str/blank? val)
                                (first path))
                           (and (= [] path)
                                (list? pred)
                                (= 'contains? (first pred))
                                (nth pred 2)))]
       ;; Jos kenttä on tyhjä ja siinä on validointiongelma
       ;; tai polku on tyhjä ja predikaatti on contains?
       ;; lisätään puuttuva pakollinen kenttä
       (update virheet :harja.ui.lomake/puuttuvat-pakolliset-kentat
               conj puuttuva)

       ;; Yritetään muodostaa virheestä selkokielinen viesti
       ;; ja lisätään se polun perusteella
       (if-let [virhe (and (not (empty? path))
                           (pred-virhe problem))]
         (update-in virheet
                    (into [:harja.ui.lomake/virheet] path)
                    (fn [kentan-virheet]
                      (if kentan-virheet
                        (into kentan-virheet virhe)
                        [virhe])))
         virheet)))
   {:harja.ui.lomake/puuttuvat-pakolliset-kentat #{}
    :harja.ui.lomake/virheet {}}
   problems))

(def viime-virheet (atom nil))

(defn validoi-spec
  "Validoi lomakedata annettua speciä vasten. Palauttaa mäpin, jossa
  :harja.ui.lomake/puuttuvat-pakolliset-kentat ja :harja.ui.lomake/virheet."
  [data spec]
  (let [problems (::s/problems (s/explain-data spec data))]

    (reset! viime-virheet problems)
    (println "DATA: " data)
    (println "virheet: " problems)
    (virheet problems)))
