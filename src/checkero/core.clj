(ns checkero.core
  (:require [clojure.walk :as w]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [arlib.io :as kio]
            )
  (:gen-class)
  )

(defmacro dbg[x] `(let [x# ~x] (println "dbg:" '~x "=" x#) x#))

(defn transform [prefix counter]
  (symbol (str prefix (swap! counter inc)))
  )

(defn normalize-form [form]
  {:pre [(seq? form)]
   :pos [(seq? %)]
   }  
  "Transform form into something that can be matched more consistently
   Non-defined identifiers are normalized and other primitive values
   Remain just as they are 
   "
  (let [scounter (atom 0)
        kcounter (atom 0)
        strcounter (atom 0)
        ]  
    (w/postwalk #(cond (special-symbol? %) %
                       (keyword? %) (transform "k" scounter)
                       (string? %) (transform "str" strcounter)
                       (and (symbol? %) (nil? (resolve %))) (transform "s" scounter)
                       :else %)
                form)
    ))


(defn transform-2 [prefix variable base]
  "Locally transform the list of variables available"
  (let [ index  (get @base variable (count @base))
        ]
    ;; update base
    (swap! base #(assoc % variable index))
    (str prefix index)
   ))

(defn normalize-form-2 [form]
  
  {:pre [(seq? form)]
   :pos [(seq? %)]
   }  
  "Transform form into something that can be matched more consistently
   Non-defined identifiers are normalized and other primitive values
   Remain just as they are 
   "
  (let [scounter (atom {})
        kcounter (atom {})
        strcounter (atom {})
        ]  
    (w/postwalk #(cond (special-symbol? %) %
                       (keyword? %) (transform-2 "k" % scounter)
                       (string? %) (transform-2 "str" % strcounter)
                       (and (symbol? %)  (try (nil? (resolve %)) (catch Throwable e  true) )) (transform-2 "s" % scounter)
                       :else %)
                form)))




(defn expand [normalized-form]
  (let [ xs (atom [])
        ]
    (w/postwalk #(do (swap! xs conj %) %)
                normalized-form)
    (deref xs)
    ))

(defn node-count [form] (count (filter #(not (seq? %)) (expand form))))

(defn update-mset [ms val]
  "multi-set creation helper function"
  (assoc ms val (inc (get ms val 0))))

(defn mset [xs]
  "Creates a map-based multi-set (key -> multiplicity)"
  (reduce update-mset {} xs))

(defn update-mset-list [cnt [k v]]
  (assoc cnt k (conj (get cnt k []) v) )
  )

(defn mset-list [xs]
  "Receives a list of the form: [[k1 v1] [k2 v2] ...]
   and creates a multiset of the values themselves instead of only
   counting them as mset
   "
   (reduce update-mset-list {} xs)
  )

(defn mset-count [ms]
  {:pre [(map? ms)]
   :pos [(integer? %)]}
  "Count number of elements in ms"
  (reduce (fn [cnt [k v]] (+ cnt v)) 0 ms))



(defn mset-distance [m1 m2]
  "Calculates the L1 distance of the multi-sets"
  (let [ dist-a (reduce + (map (fn [[key multi]] (Math/abs (- multi (get m2 key 0))) ) m1))
        dist-b (reduce + (map (fn [[key multi] ] (if (contains? m1 key) 0 multi)) m2))
        ]
    (+ dist-a dist-b)
    ))

(defn mtd [nfa nfb]
  {:pre [(coll?  nfa) (coll? nfa)]
   :pos [(integer? %)]
   }
  "Calculates something close to the MTD distance over two normalized forms:
   Paper: A Tree Distance Function Based on Multi-sets
   Date:  (2009, Springer)
   By:    Arnoldo Jose Muller-Molina
          Kouichi Hirata
          Takeshi Shinohara
  "
  (let [nfa-mset (mset (expand nfa))
        nfb-mset (mset (expand nfb))
        ;a-count (mset-count nfa-mset)
        ;b-count (mset-count nfb-mset)
        ]
    ;(double (/ (mset-distance nfa-mset nfb-mset) (+ a-count b-count)))
    (mset-distance nfa-mset nfb-mset)
    ))

(defn mtd-entry [a b]
  (mtd (:normalized a) (:normalized b) ))

;; for each entry, we store the student name
;; The file-name where the expression was found
;; original expression
;; and normalized expression
(defrecord Entry [^String student file-name original normalized])

(defrecord Student [^String student dir-name entries])


(defn load-student-file [m student-name file-name]
  {:pre [(.exists file-name) (integer? m)]
   :pos [(seq? %)]
   }
  "Loads a student file, returns a list of entries"
  (let [ expressions (read-string (str "[" (slurp (dbg file-name)) "]"))
         correct-size-expressions (filter #(>= (node-count %) m) expressions)
         entries (map #(Entry. student-name file-name % (normalize-form-2 %)) correct-size-expressions)
        ]
    entries))


(defn load-student [m student-name dir-name]
  {:pre [(string? student-name) (.exists (io/as-file (dbg dir-name))) (integer? m)]
   :pos [(seq? %)]
   }
  "loads all files presented by a student"
  (let [ignore #{"project.clj"} ; files to ignore 
        files (filter #(not (contains? ignore (.getName %)))  (filter #(.endsWith (.getName %) "clj") (file-seq  (io/as-file dir-name))))
        full-entries (mapcat #(load-student-file m student-name %) files)
        ]
    (Student. student-name dir-name full-entries)))

(defn update-queue [queue distance value]
  (assoc queue distance (conj (get queue distance []) value)))

(defn remove-distance [queue distance]
  (let [ updated-value (rest (get queue distance))
        ]
    (if (empty? updated-value)
      (dissoc queue distance)
      (assoc queue distance updated-value))))

(defn count-queue [queue]
  (reduce (fn [cnt [k v]] (+ cnt (count v))) 0 queue))

(defn sim-machine [queue query target k range f]
  (let [ distance (f query target)
        ]
    (cond (> distance range)
          queue
          (< (count-queue queue) k)
          (update-queue queue distance target)
          :else
          (let [[current-largest value] (last queue)
                ]
            (if (> current-largest distance)
              (update-queue (remove-distance queue current-largest) distance target)
              queue)))))

(defn t-distance [a b] (Math/abs (- a b)))

(defn expand-queue-result [queue]
  (reduce (fn [cnt [k vs]] (concat cnt (map #(vector k %1) vs))) [] queue ))

(defn similarity-search [query db k range f]
  {:pre [(integer? k) (number? range)]
   :pos [(seq? %)]
   }
  "Calculates the k-NN of the given query against db   
   "
  (expand-queue-result (reduce #(sim-machine %1 query %2 k range f) (sorted-map) db)))



(defn report-student [k range student all-students]
  "find all the closest matches for each student,
   sorts result by quality of the match
   "
  (let [ to-match (mapcat :entries (filter #(not= %1 student) all-students))
        matches (filter (fn [[_ results]] (not (empty? results))) (map #(vector %1 (similarity-search  %1 to-match k range mtd-entry)) (:entries student) ))
        sorted-matches (sort-by (fn [m] (let [ [_ [[distance] _]] m] (do (when-not (integer? distance) (dbg distance)) distance)) ) matches)
        ]
    [student sorted-matches]))



(defn pretty-print-match [student [query match]]
  (apply str (concat ["-------------------------------" "Student code: " student
                      "\n[Query]"
                      "\nOriginal:   " (:original query)
                      "\nNormalized: " (:normalized query)
                      ]
                    
                                      (map (fn [[distance value]]
                                             (str "\n>>> " (:student value) " [" (:file-name value) "]"
                                                  "\nDistance:   " distance
                                                  "\nOriginal:   " (:original value)
                                                  "\nNormalized: " (:normalized value)))
                             match))))

(defn pretty-print-matches [match]
  (let [[student matches] match
        ]
    (apply str (str/join "\n" (concat (list (str "Reviewing student: " (:student student)))
                                             (map #(pretty-print-match (:student student) %) matches))))))

(defn print-entry [^Entry e]
  (str ">>>          " (:student e) "\n"
       "             [" (:file-name e) "]\n"
       "[Original]   " (:original e) "\n"
       "[Normalized] " (:normalized e)  "\n"
       ))

(defn mset-to-str [ms]
  "Receives a counting multi-set and pretty prints it"
  (apply str (map (fn [[k cnt]] (str "[" k " " cnt "] ")) (reverse (sort-by (fn [[k cnt]] cnt) ms)))))

(defn mset-list-to-str [ml]
  "Receives a multi-set-list and pretty prints it for human reading"
  (apply str (map (fn [[distance value]] (str "<| " distance " " (mset-to-str (mset value))  "|>\n" )  )
                 (sort-by  first ml) )))

(defn -main [mnode hp kp rangep source-folder]
  "Execute checkero with the following parameters:
   > mnode: minimum number of nodes per expression
   > h: Get the top h hotspots
   > k: amount of closest matches per query (we suggest to employ the total number of students here + c)
   > range: maximum tolerable distance
   > args: comes in the following shape: <Student Name1> <Student Dir1> <Student Name2> <Student Dir2> ....
     where <Student NameN> holds a student name and <Student DirN> holds the directory where
     all the homework files are located
   "
  (let [m (Long/parseLong mnode)
        h (Long/parseLong hp)
        k (Long/parseLong kp)
        range (Double/parseDouble rangep)
        student-dirs (-> source-folder kio/list-files  kio/filter-folders)
        students (map (fn [dir] (load-student m (.getName dir) dir)) student-dirs )
        results (map #(report-student k range %1 students) students)
        hot-spots (mapcat (fn [[student matches]]
                            (mapcat (fn [[query match]]
                                      (map (fn [[distance value]] value)  match))
                                    matches))
                          results)
        multi-set (mset hot-spots)
        sorted-hot-spots (take h (reverse (sort-by (fn [[k multi]] multi) multi-set)))
        ;; each student and all the matches of the form [distance Entry]
        sudent-vs-all-matches (map (fn [[student matches]]
                                       [student (mapcat (fn [[query match]]
                                                          match)
                                                        matches)])
                                   results)
        student-vs-all-matches-mset (map (fn [[student matches]]
                                           [student
                                            (mset-list
                                             (map (fn [[distance value]] [distance (:student value)])  matches))] )
                                         sudent-vs-all-matches )
        
        ]
    (println "-------------------------------------")
    (println "Checkero finds collective inspiration")
    (println "-------------------------------------")
    (doseq [match results]
      (let [[student matches] match
            ]
        (spit (str (:dir-name student) "/checkero.txt") (pretty-print-matches match))
        (println "<> Processing student: " (:student student))))

    (println "@@@ Commonly found expressions in the homework folder:")
    (doseq [[hot multi] sorted-hot-spots]
      (println (print-entry hot)  "[Multiplicity]   " multi)
      )
    (println "Close student matches")
    (doseq [[student distribution] student-vs-all-matches-mset]
      (println "### " (:student student) )
      (print (mset-list-to-str distribution ))
      )
    (println "Work complete!")
    ))