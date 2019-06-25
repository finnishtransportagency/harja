(ns harja.id
  "Monessa paikassa, erityisesti esim palveluissa asiota luodessa/muokattaessa,
  joudutaan tarkastamaan, onko tallennettava asia uusi, vai ei - eli onko sillä id, vai ei.
  Yleensä olematon id on nil, mutta gridin riveille annetaan negatiivinen id, joka joskus
  annetaan eteenpäin palveluille.")

;; Tämän namespacen sisällön voisi siirtää toiseen namespaceen, mutta haluamme
;; välttää util.cljc tyyppisiä namespaceja

(defn id-olemassa?
  "Ottaa numeron, ja palauttaa true, jos kyseessä on positiivinen numero.

  Uusille asioille id on yleensä nil, mutta gridiin lisätyille riveille id
  on negatiivinen numero."
  [id]
  (and
    (some? id)
    (integer? id)
    (pos? id)))
