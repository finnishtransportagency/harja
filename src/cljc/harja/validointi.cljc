(ns harja.validointi
  "Validointeja jotka toimivat sekä cljs- että clj-koodissa."
  (:require
    [clojure.string :as str]))

(defn validoi-numero
  "Validaattori numeroille, params: [numero alaraja ylaraja desimaalien-max-maara]"
  [numero-str alaraja ylaraja desimaalien-max-maara]
  #?(:cljs
     (if (str/blank? numero-str)
       ;; Jos numeroa ei ole annettu, on se sallittu
       true
       ;; Muutoin tarkistetaan että numero on sallituissa rajoissa
       (let [numero-str (str/replace numero-str "," ".")
             desimaalit (count (second (str/split numero-str #"\.")))
             numero (js/Number numero-str)]

         (and
           (<= alaraja numero ylaraja)
           ;; Estetään kirjoittamasta , tai . kokonaislukuun
           (or (not= 0 desimaalien-max-maara) (not (str/includes? numero-str ".")))
           (<= desimaalit desimaalien-max-maara)))))

  ;; clj-osiolla ei vielä käyttäjiä, tee desim. käsittely jos tarvitaan joskus
  #_:clj
  #_(<= alaraja numero ylaraja))

(def email-regexp #"^([a-zA-Z0-9_\-\.]+)@([a-zA-Z0-9_\-\.]+)\.([a-zA-Z]{2,5})$")

(defn validoi-email
  "Yksinkertainen emailosoitteen-validaattori, joka toimii paremmin kuin string?"
  [email]
  (when email
    (re-matches email-regexp email)))

;; Arvot on saatu https://epsg.io/ palvelusta ETRS89/ TM35FIN -projektiota käyttämällä hakemalla
;; Suomen pohjoisin, eteläisin, itäisin ja lähntisin piste käsin kartalta.
(def max-y-koordinaatti 7777222)
(def min-y-koordinaatti 6631383)
(def min-x-koordinaatti 73963)
(def max-x-koordinaatti 733682)

(defn onko-koordinaatit-suomen-alueella?
  "Validoi koordinaatit ETRS89/ TM35FIN -projektiota käyttäen."
  [x y]
  (if (and (<= min-x-koordinaatti x max-x-koordinaatti)
        (<= min-y-koordinaatti y max-y-koordinaatti))
    true
    false))
